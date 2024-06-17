package com.heima.article.stream;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.mess.UpdateArticleMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class HotArticleStreamHandler {

    @Bean
    public KStream<String, String> kStream(StreamsBuilder streamsBuilder) {
        //接收消息，和發送消息的TOPIC對應
        KStream<String, String> stream = streamsBuilder.stream(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC);
        //聚合流式处理
        stream.map((key, value) -> {
                    //value 是 UpdateArticleMess JSON
                    UpdateArticleMess mess = JSON.parseObject(value, UpdateArticleMess.class);
                    //重置消息的key: 1234343434（id）   和  value: likes:1 （操作類型：操作）
                    return new KeyValue<>(mess.getArticleId().toString(), mess.getType().name() + ":" + mess.getAdd());
                })
                //按照文章id进行聚合
                .groupBy((key, value) -> key)
                //时间窗口
                .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))
                /**
                 * 自行的完成聚合的计算
                 */.aggregate(new Initializer<String>() {
                    /**
                     * 初始方法，返回值是消息的value
                     * @return
                     */
                    @Override
                    public String apply() {
                        return "COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0";
                    }
                    /**
                     * 真正的聚合操作，返回值是消息的value
                     */
                }, new Aggregator<String, String, String>() {
                    @Override
                    public String apply(String key, String value, String aggValue) {
                        //如果是第一次
                        if (StringUtils.isBlank(value)) {
                            return aggValue;
                        }
                        //第二次之后
                        //e.g."COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0"
                        String[] aggAry = aggValue.split(",");
                        int col = 0, com = 0, lik = 0, vie = 0;
                        //遍歷arrAry
                        for (String agg : aggAry) {
                            //分割agg, 内容是“COLLECTION COMMENT LIKES VIEWS”其一
                            String[] split = agg.split(":");
                            /**
                             * 根據MessPojo（傳入的值）和spilt比較
                             * 获得初始值，也是时间窗口内计算之后的值
                             * 如：UpdateArticleType.valueOf("COLLECTION")
                             */
                            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])) {
                                case COLLECTION:
                                    //從aggValue中取值以為對應的單位賦值
                                    col = Integer.parseInt(split[1]);
                                    break;
                                case COMMENT:
                                    com = Integer.parseInt(split[1]);
                                    break;
                                case LIKES:
                                    lik = Integer.parseInt(split[1]);
                                    break;
                                case VIEWS:
                                    vie = Integer.parseInt(split[1]);
                                    break;
                            }
                        }
                        /**
                         * 真正的流式計算
                         * 累加操作,不從aggValue取得數值,而是操作value值（從mess 裏獲取），最後更新對應的數值
                         */
                        String[] valAry = value.split(":");
                        switch (UpdateArticleMess.UpdateArticleType.valueOf(valAry[0])) {
                            case COLLECTION:
                                col += Integer.parseInt(valAry[1]);
                                break;
                            case COMMENT:
                                com += Integer.parseInt(valAry[1]);
                                break;
                            case LIKES:
                                lik += Integer.parseInt(valAry[1]);
                                break;
                            case VIEWS:
                                vie += Integer.parseInt(valAry[1]);
                                break;
                        }
                        //把上面的結果拼接至對應格式並返回
                        String formatStr = String.format("COLLECTION:%d,COMMENT:%d,LIKES:%d,VIEWS:%d", col, com, lik, vie);
                        System.out.println("文章的id:" + key);
                        System.out.println("当前时间窗口内的消息处理结果：" + formatStr);
                        return formatStr;
                    }
                }, Materialized.as("hot-atricle-stream-count-001"))
                .toStream()
                .map((key, value) -> {
                    //formatObj： 改變value為有 id + formatStr内容重新整理為obj
                    //key=id,value = MsgPojo(id,like,collect,views...)
                    return new KeyValue<>(key.key().toString(), formatObj(key.key().toString(), value));
                })
                //发送消息
                .to(HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC);

        return stream;
    }

    /**
     * 格式化消息的value数据
     *
     * @param articleId
     * @param value
     * @return
     */
    public String formatObj(String articleId, String value) {
        ArticleVisitStreamMess mess = new ArticleVisitStreamMess();
        mess.setArticleId(Long.valueOf(articleId));

        String[] valArr = value.split(",");
        for (String val : valArr) {
            String[] split = val.split(":");
            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])) {
                case COLLECTION:
                    mess.setCollect(Integer.parseInt(split[1]));
                    break;
                case  COMMENT:
                    mess.setComment(Integer.parseInt(split[1]));
                    break;
                case LIKES:
                    mess.setLike(Integer.parseInt(split[1]));
                    break;
                case  VIEWS:
                    mess.setView(Integer.parseInt(split[1]));
                    break;
            }
        }
        return JSON.toJSONString(mess);
    }
}