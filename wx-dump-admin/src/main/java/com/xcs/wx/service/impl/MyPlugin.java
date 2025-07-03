package com.xcs.wx.service.impl;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.net.URLEncodeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.xcs.wx.constant.DataSourceType;
import com.xcs.wx.domain.Msg;
import com.xcs.wx.domain.vo.MsgVO;
import com.xcs.wx.domain.vo.SessionVO;
import com.xcs.wx.mapper.MsgMapper;
import com.xcs.wx.msg.MsgStrategy;
import com.xcs.wx.msg.impl.CardLinkMsgStrategy;
import com.xcs.wx.service.MsgService;
import com.xcs.wx.service.SessionService;
import com.xcs.wx.util.DateFormatUtil;
import com.xcs.wx.util.LZ4Util;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

//@Component
@RestController
public class MyPlugin {
    private static String WX_CFG = "D:/Read/static/read_time.json";
    public static JsonNode json;

    static {
        try {
            json = new ObjectMapper().readTree(Files.readString(Path.of(WX_CFG)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static long orDefaultSeq(Long nextSequence, String talker) {
        return Optional.ofNullable(nextSequence).filter(x -> x > 0).orElse(json.path(talker).asLong());
    }

    public static String orDefaultKey(String key) {
        return StrUtil.isNotBlank(key) ? key : "ff2cd5e5eb164ccdbb838e86291cd3528a415d6599e14cfd93d7fd448648488e";
    }

    public static final String Q_SIZE = "500";


    @Component
    public static class MsgStrategy__ implements MsgStrategy {

        //public static final String TYPE_TEXT = "1_0";
        //public static final String TYPE_IMG = "3_0";
        //public static final String TYPE_VOICE = "34_0";
        //public static final String TYPE_VIDEO = "43_0";
        //public static final String TYPE_EMOJI_PKG = "47_0";
        public static final String TYPE_N = "49_1";
        public static final String TYPE_LINK_CARD1 = "49_4";
        //public static final String TYPE_LINK_CARD = "49_5";
        public static final String TYPE_FILE = "49_6";
        public static final String TYPE_EMOJI_GIF = "49_8";
        public static final String TYPE_FORWARD_MSGS = "49_19";
        //public static final String TYPE_MINI_APP = "49_33";
        //public static final String TYPE_MINI_APP2 = "49_36";
        public static final String TYPE_VIDEOS_CHANNEL = "49_51";
        public static final String TYPE_JIELONG = "49_53";
        //public static final String TYPE_REF = "49_57";
        public static final String TYPE_LIVE = "49_63";
        public static final String TYPE_NOTICE = "49_87";
        public static final String TYPE_LIVE2 = "49_88";
        public static final String TYPE_TRANS = "49_2000";
        public static final String TYPE_RED_PACKET_COVER = "49_2003";
        //public static final String TYPE_SYS = "10000_0";
        public static final String TYPE_SYS_HIT = "10000_4";
        public static final String TYPE_SYS_CANCEL = "10000_57";
        public static final String TYPE_SYS_INVITE = "10000_8000";


        @Autowired
        private CardLinkMsgStrategy cardLinkMsgStrategy;

        @Override
        public boolean support(Integer type, Integer subType) {
            return SpringUtil.getBeansOfType(MsgStrategy.class)
                    .values().stream().noneMatch(x -> x != this && x.support(type, subType));
        }

        @Override
        public void process(MsgVO msgVO) {
            String text;
            String _type = msgVO.getType() + "_" + msgVO.getSubType();
            switch (_type) {
                case TYPE_N -> {
                    text = "【消息？】";
                    //msg.text += titleMsg(compressContents);
                    cardLinkMsgStrategy.process(msgVO);
                }
                case TYPE_LINK_CARD1/*, TYPE_LINK_CARD*/ -> {
                    text = "【卡片链接1】";
                    //String xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
                    //text += cardMsg(xml);
                    cardLinkMsgStrategy.process(msgVO);
                }
                case TYPE_FILE -> text = "【文件】";
                case TYPE_EMOJI_GIF -> text = "【GIF表情】";
                case TYPE_FORWARD_MSGS -> {
                    text = "【聊天记录】";
                    //tring xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
                    //text += cardMsg(xml);
                    cardLinkMsgStrategy.process(msgVO);
                }
                case TYPE_VIDEOS_CHANNEL -> {
                    text = "【视频号】";
                    //String xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
                    //ext += xml.select("nickname").text();
                }
                case TYPE_LIVE -> text = "【直播】";
                case TYPE_NOTICE -> {
                    text = "【群公告】";
                    StringBuilder _text = new StringBuilder();
                    Opt.ofNullable(msgVO.getCompressContent())
                            .map(compressContent -> LZ4Util.decompress(msgVO.getCompressContent()))
                            //.map(xmlContent -> XmlUtil.parseXml(xmlContent, Map.class))
                            .ifPresent(xmlContent -> {
                                try {
                                    _text.append(new XmlMapper().readTree(xmlContent).findValue("textannouncement").asText());
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                    text += _text.toString();
                    //text += xml.select("textannouncement").text();
                }
                case TYPE_LIVE2 -> text = "【直播】";
                case TYPE_TRANS -> text = "【转账】";
                case TYPE_RED_PACKET_COVER -> text = "【红包封面】";
                //case TYPE_SYS -> {
                //    text = "【系统通知】";
                //    text += msgVO.getStrContent() + StringEscapeUtils.escapeHtml4(LZ4Util.decompress(msgVO.getCompressContent()));
                //}
                //case TYPE_SYS_HIT, TYPE_SYS_CANCEL -> {
                //    text = "[拍一拍]";
                //    text += strContent;
                //    from = null;
                //}
                //case TYPE_SYS_INVITE -> {
                //    text = "【邀请入群】";
                //    text += strContent;
                //}
                case TYPE_JIELONG -> {
                    text = "【接龙】";
                    //text += titleMsg(compressContents);
                    cardLinkMsgStrategy.process(msgVO);
                }
                default -> {
                    text = "【未定义】" + _type;
                    //text += msgVO.getCompressContent() == null ? "" : StringUtils.left(LZ4Util.decompress(msgVO.getCompressContent()), 100);
                }
            }

            if (msgVO.getCardLink() != null) {
                msgVO.getCardLink().setTitle(text + msgVO.getCardLink().getTitle());
                msgVO.getCardLink().setSourceDisplayName(text + msgVO.getCardLink().getSourceDisplayName());
                //msgVO.getCardLink().setDes(text + msgVO.getCardLink().getDes());
                msgVO.setStrContent(text);

                msgVO.setType(49);
                msgVO.setSubType(5);
            } else {
                msgVO.setStrContent(msgVO.getStrContent() + text);
            }
        /*if (StringUtils.isEmpty(msgVO.getStrContent())) {
            msgVO.setStrContent(text);
        }*/
        }
    }




    @Autowired
    private MsgService msgService;
    @Autowired
    private SessionService sessionService;

    private Map<String, Long> unreadCounts = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean isTaskRunning = new AtomicBoolean(false);

    @GetMapping(value = "html/chats", produces = "text/html;charset=UTF-8")
    public String getChats() {
        List<SessionVO> sessions = sessionService.querySession().stream().filter(x -> x.getUserName().endsWith("@chatroom")).toList();


        Map<String, Long> allUnreadTimes = sessions.stream().map(SessionVO::getUserName).collect(Collectors.toMap(x -> x, x -> json.path(x).asLong()));

        if (isTaskRunning.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    unreadCounts = msgRepositoryImpl__.queryUnreadCounts(allUnreadTimes);
                } finally {
                    isTaskRunning.set(false);
                }
            });
        }

        unreadCounts = msgRepositoryImpl__.queryUnreadCounts(allUnreadTimes);

        /*String html = sessions.stream()
                .filter(x -> x.getUserName().endsWith("@chatroom"))
                .map(x -> """
                        <div><a href="msg/{{1}}">{{2}}</a></div>"""
                        .replace("{{1}}", x.getUserName() + "?_n=" + x.getNickName())
                        .replace("{{2}}", x.getNickName()))
                .collect(Collectors.joining());*/

        StringBuilder html = new StringBuilder("<table>");
        sessions.forEach(x ->
                html.append("""
                        <tr>
                            <td><a style="display: block" href="msg/%s" onclick="parentNode.nextElementSibling.firstChild.style.background='#888'">%s</a></td>
                            <td style="text-align:right"><span style="background: black; color: white; border-radius: 15px;">%s</span></td>
                            <td style="text-align:right">%s</td>
                        </tr>
                        """.formatted(x.getUserName(), x.getNickName(),
                        Optional.ofNullable(unreadCounts.get(x.getUserName())).map(y -> "&nbsp;" + y + "&nbsp;").orElse(""),
                        DateFormatUtil.formatTimestamp(x.getTime()))
                )
        );
        html.append("</table>");

        return "<a href=\"/api/database/decrypt?pid=12836&basePath=C%3A%5CUsers%5CAdministrator%5CDocuments%5CWeChat+Files&wxId=wxid_al3hubj4v69x22&nickname=%E5%8D%8E%E5%93%A5&version=3.9.12.51&account=wen-qianghua&mobile=13651862250\">**解密数据**</a><br>"
                + html
                + "<script src=\"https://wenqh.github.io/page.js\"></script>";
    }

    @GetMapping(value = "html/msg/{talker}", produces = "text/html;charset=UTF-8")
    public String getMsg(@PathVariable String talker, Long nextSequence, @RequestParam(defaultValue = Q_SIZE) Integer size/*负数代表往前查*/, String _n) {
        List<MsgVO> msgList = msgService.queryMsg(talker, nextSequence, size);


        String html = "";

        for (int i = 0; i < msgList.size(); i++) {
            MsgVO x = msgList.get(i);

            String str = "";
            if (i > 0 && x.getCreateTime() - msgList.get(i - 1).getCreateTime() > 10 * 60) {
                //str = "<div class=\"time\">" + FormatTimeUtil.format(LocalDateTime.ofInstant(Instant.ofEpochSecond(m.time), ZoneId.systemDefault())) + "</div>";
                //str = "<div class=\"sys\" style=\"text-align: right\">" + DateFormatUtil.formatTimestamp(x.getCreateTime()) + "</div>";
            }
            if (i > 0 && Optional.of(msgList.get(i - 1)).filter(y -> !x.getNickname().equals(y.getNickname()) && x.getType() != 1000).isPresent()) {
                str += "<div class=\"nickname\">●" + x.getNickname() + "</div>";
            }

            str += "<div class=\"msg" + (x.getType() != 10000 ? "" : " sys") + "\">";


            String msg = x.getStrContent();
            if (x.getThumb() != null) {
                //msg = "<img src=\"" + "/img/wx/" + x.getThumb().substring(x.getThumb().lastIndexOf("\\") + 1, x.getThumb().length() - 4) + "\">";
                msg = "<img src=\"/api/image/downloadImgFormLocal?localPath=" + URLEncodeUtil.encode(x.getThumb()) + "\">";
            }
            if (x.getImage() != null) {
                //msg += "<img data-src=\"" + "/img/wx/" + x.getImage().substring(x.getImage().lastIndexOf("\\") + 1, x.getImage().length() - 4) + "\">";
                //msg += "<img src=\"/api/image/downloadImgFormLocal?localPath=" + URLEncodeUtil.encode(x.getImage()) + "\">";
            }

            if (x.getReferMsgContent() != null) {
                msg = "<blockquote>" + x.getReferMsgContent()/*.replaceFirst("：\\n?", "\n")*/ + "</blockquote>" + x.getStrContent();
            }
            if (x.getEmojiUrl() != null) {
                msg = "<img src=\"" + x.getEmojiUrl() + "\">";
            }
            if (x.getWeAppInfo() != null) {
                msg += "[小程序]\n" + "<img src=\"" + x.getWeAppInfo().getWeAppPageThumbRawUrl() + "\">"
                        + "\n<b>" + x.getWeAppInfo().getSourceDisplayName() + "</b>"
                        + "\n" + x.getWeAppInfo().getTitle();
            }
            if (x.getCardLink() != null) {
                msg += "<a href=\"" + x.getCardLink().getUrl() + "\">" + x.getCardLink().getTitle() + "</a>"
                        + "\n" + Optional.ofNullable(x.getCardLink().getDes()).orElse("");
            }


            msg = msg.startsWith("【未定义】") ? StringEscapeUtils.escapeHtml4(msg) : msg;
            str += msg;
            str += "<div class=\"time\">" + DateFormatUtil.formatTimestamp(x.getCreateTime()) + "</div><div></div>";
            //str += "<div class=\"time\">" + DateFormatUtil.formatTimestamp(x.getCreateTime()) + "</div>";
            str += "</div><br>";

            //已读位置标记
            if (i % 50 == 49 || i == msgList.size() - 1) {
                str += "<div class=\"time-anchor\" data-q=\"chat=%s&time=%s\"></div>".formatted(x.getStrTalker(), x.getSequence());
            }

            html += str;
        }


        return """
                    <html lang="zh-CN">
                    <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                    <title>{{title}}</title>
                    <style>/*微信*/
                        .nickname {
                            font-size: 0.75em;
                            color: #899;
                            margin-top: 10px;
                        }
                        .msg {
                            margin: 1px 8px 1px 13px;
                            border: 1px solid;
                            border-radius: 10px;
                            white-space: pre-wrap;
                            padding: 2px 5px;
                            display: inline-block;
                            
                            overflow-wrap: break-word; word-break: break-word;

                            /*气泡方案二 display: block; width: fit-content 如果时间浮动则要加.msg::after {content: ""; display: block; clear: both;}*/
                            /*右下时间方案二 相对定位 .msg:relative; time:absolute; .msg::after { content: ''; display: inline-block; width: 26px;}*/                
                            position: relative;
                        }
                        .msg img {
                            min-width: 100px;
                            min-height: 100px;
                            max-width: 100%;
                            /*vertical-align: top;*/
                        }
                        .msg blockquote {
                            border-left: 5px solid #888;
                            font-size: 0.75em;
                            padding-left: 5px;
                            margin: 0;
                            color: #888;
                        }
                        /*.msg p::first-line {
                            color: #888
                        }*/
                        
                        .msg .time {
                            color: #aaa;
                            font-size: 0.65em;
                            
                            /*右下角时间方案二，有问题不会底部对齐 float: right;*/
                            position: absolute; right: 5px; bottom: 0;
                        }
                        .msg .time + div {
                            display: inline-block;
                            width: 30px;
                        }

                        .sys {
                            text-align: center;
                            color: #999;
                            font-size: 0.83em;
                        }
                        
                        </style>
                        </head>
                
                        <body style="font-size: 1.2em">
                        <div><a href="?nextSequence={{firstSeq}}&size=-100">上一页</a></div>
                        {{body}}
                        <hr><div style="text-align: center">{{endTip}}</div>
                        </body>
                        <script src="https://wenqh.github.io/page.js"></script>
                        <script>
                        function _hook() {
                            for (const el of document.querySelectorAll('.time-anchor')) {
                                const { top } = el.getBoundingClientRect();
                                if (top >= 0 && top < window.innerHeight) {
                                    el.style.backgroundImage = `url('/read-time?${el.dataset.q}')`;
                                    return;
                                }
                            }
                        }
                        _hook();
                        </script>
                    </html>
                """.replace("{{body}}", html)
                .replace("{{firstSeq}}", msgList.stream().findFirst().map(MsgVO::getSequence).map(String::valueOf).orElse(""))
                //.replace("{{endTip}}", msgList.size() < size ? "看完了" : "下一页" );
                .replace("{{endTip}}", msgList.size() < size ? "看完了" : "<a href=\"\">刷新继续</a>")
                .replace("{{title}}", String.valueOf(_n));
    }

    @GetMapping("read-time")
    public String setReadTime(String chat, long time) throws IOException {
        if (time > json.path(chat).asLong()) {
            Files.writeString(Path.of(WX_CFG), ((ObjectNode) json).put(chat, time).toPrettyString());
        }
        return "";
    }

    @Autowired
    private MsgRepositoryImpl__ msgRepositoryImpl__;

    @Repository
    public static class MsgRepositoryImpl__ extends ServiceImpl<MsgMapper, Msg> {
        public Map<String, Long> queryUnreadCounts(Map<String, Long> chatLastTimeMap) {
            Map<String, Long> unreadCounts = new HashMap<>();

            for (String poolName : DataSourceType.getMsgDb()) {
                DynamicDataSourceContextHolder.push(poolName);
                try {
                    QueryWrapper<Msg> queryWrapper = new QueryWrapper<>();

                    // OR 条件组合查询
                    queryWrapper.and(wrapper -> {
                        chatLastTimeMap.forEach((strTalker, lastReadTime) ->
                                wrapper.or(w -> w.eq("StrTalker", strTalker).gt("Sequence", lastReadTime))
                        );
                    });

                    // 查询 StrTalker 和 count
                    List<Map<String, Object>> result = this.getBaseMapper()
                            .selectMaps(queryWrapper
                                    .select("StrTalker", "COUNT(*) AS count")
                                    .groupBy("StrTalker"));

                    // 聚合合并结果
                    result.forEach(m -> {
                        String talker = m.get("StrTalker").toString();
                        Number count = (Number) m.get("count");
                        unreadCounts.merge(talker, count.longValue(), Long::sum);
                    });

                } finally {
                    DynamicDataSourceContextHolder.clear(); // 确保释放数据源
                }
            }

            return unreadCounts;
        }
    }
}
