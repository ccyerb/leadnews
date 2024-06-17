package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {
    /**
     * 添加延迟任务
     *
     * @param task
     * @return
     */
    @Override
    public long addTask(Task task) {
        //1.添加任务到数据库中, 包括taskInfo 和 taskInfo_logs

        boolean success = addTaskToDb(task);
        //成功添加后才執行
        if (success) {
            //2.添加任务到redis
            addTaskToCache(task);
        }
        return task.getTaskId();
    }

    @Override
    public boolean cancelTask(long taskId) {
        boolean flag = false;
        //取得並删除任务，更新日志
        Task task = updateDb(taskId,ScheduleConstants.EXECUTED);
        //根據任務删除redis的数据
        if(task != null) {
            removeTaskFromCache(task);
            flag = true;
        }
        return false;
    }

    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try {
            String key = type+"_"+priority;
            //獲取當前任務(TOPIC)
            String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            //任務存在
            if(StringUtils.isNotBlank(task_json)){
                task = JSON.parseObject(task_json, Task.class);
                //更新数据库信息
                updateDb(task.getTaskId(),ScheduleConstants.EXECUTED);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("poll task exception");
        }

        return task;
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh() {
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if(StringUtils.isNotBlank(token)) {
            System.out.println(System.currentTimeMillis() / 1000 + "执行了定时任务");

            // 获取所有未来数据集合的key值 (FUTURE)
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            //遍歷每一組key，每組key都有1 set tasks
            for (String futureKey : futureKeys) { // future_250_250
                //把所有FUTURE_KEY變爲TOPIC_KEY -> topic_250_250
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];
                //從redis获取该组key下当前需要消费的任务数据（未來任務）
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
                if (!tasks.isEmpty()) {
                    //将这些任务数据添加到消费者队列中
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    System.out.println("成功的将" + futureKey + "下的当前需要执行的任务数据刷新到" + topicKey + "下");
                }
            }
        }
    }

    /**
     刪除redis裏所有任務， 從數據庫（只）重新獲取**5分鐘内要執行的任務並同步到redis
     周期為每五分鐘一次
     **/
    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    public void reloadData() {
        //delete all data from redis
        clearCache();
        log.info("数据库数据同步到缓存");
        Calendar calendar = Calendar.getInstance();
        //預定執行時間為當前時間的五分鐘后
        calendar.add(Calendar.MINUTE, 5);

        //從數據庫查看小于未来5分钟的所有任务（5分鐘内要執行的任務，在redis裏） (FUTURE_TASK)
        //task.getExcuteTime <= calendar.getTime()
        List<Taskinfo> allTasks = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery()
                                                .lt(Taskinfo::getExecuteTime,calendar.getTime()));
        if(allTasks != null && allTasks.size() > 0){
            for (Taskinfo taskinfo : allTasks) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo,task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                //把數據庫裏5分鐘内的任務加入redis
                addTaskToCache(task);
            }
        }
    }

    private void clearCache(){
        // 删除缓存中未来数据集合和当前消费者队列的所有key
        Set<String> futurekeys = cacheService.scan(ScheduleConstants.FUTURE + "*");// future_
        Set<String> topickeys = cacheService.scan(ScheduleConstants.TOPIC + "*");// topic_
        cacheService.delete(futurekeys);
        cacheService.delete(topickeys);
    }
    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType()+"_"+task.getPriority();
        /**根據不同情況lRemove/zRemove**/
        if(task.getExecuteTime()<=System.currentTimeMillis()){
            cacheService.lRemove(ScheduleConstants.TOPIC + key,0,JSON.toJSONString(task));
        }
        else {
            cacheService.zRemove(ScheduleConstants.FUTURE + key,JSON.toJSONString(task));
        }
    }

    private Task updateDb(long taskId, int status) {
        Task task = null;
        try {
            //删除任务
            taskinfoMapper.deleteById(taskId);
            /**取得數據庫的任務**/
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);
            /**設定任務執行時間並返回任務**/
            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs,task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        }catch (Exception e){
            log.error("task cancel exception taskid={}",taskId);
        }
        return task;
    }

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    /**
     * 添加任务到数据库中
     *
     * @param task
     * @return
     */
    private boolean addTaskToDb(Task task) {
        //保證數據成功添加
        boolean flag = false;

        try {
            //保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            //task设置taskID, 回填
            task.setTaskId(taskinfo.getTaskId());

            //保存任务日志数据
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            //樂觀鎖
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);

            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return flag;
    }
    //封裝了redis template
    @Autowired
    private CacheService cacheService;

    /**
     * 把任务添加到redis中
     * 兩種情況會執行 1）addTask 2）reload
     *
     * @param task
     */
    private void addTaskToCache(Task task) {

        String key = task.getTaskType() + "_" + task.getPriority();

        //获取當前時間5分钟之后的时间  毫秒值
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTime = calendar.getTimeInMillis();
        //兩種情況， 1.現在可以執行 2.預定要執行
        //2.1 如果任务的执行时间小于等于当前时间（小於當前時間而不是預定執行時間），存入list
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lLeftPush(ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        }
        else if (task.getExecuteTime() <= nextScheduleTime) {
            //2.2 如果任务的执行时间大于当前时间（上面的if未通過） && 小于等于预设时间（未来5分钟） （當前條件，即任務未到預定執行時間）存入zset中
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }


    }
}
