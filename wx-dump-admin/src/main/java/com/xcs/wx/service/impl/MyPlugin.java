package com.xcs.wx.service.impl;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.xcs.wx.domain.vo.MsgVO;
import com.xcs.wx.msg.MsgStrategy;
import com.xcs.wx.msg.impl.CardLinkMsgStrategy;
import com.xcs.wx.util.LZ4Util;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@RestController
public class MyPlugin implements MsgStrategy {
    static JSONObject json;
    static File file = new File("D:/Read/static/read_time.json");

    static {
        try {
            String str = FileUtils.readFileToString(file, "UTF-8");
            json = JSONUtil.parseObj(str);
            /*//////////////////////////////////
            json.forEach((s, o) -> {
                json.set(s, ((Long) o).intValue() / 1000);
            });/////////////////*/
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static long defaultSeq(Long nextSequence, String talker) {
        if (nextSequence != 0) {
            return nextSequence;
        }

        return json.getLong(talker, 0L);
    }

    public static String orDefaultKey(String key) {
        return StrUtil.isNotBlank(key) ? key : "ff2cd5e5eb164ccdbb838e86291cd3528a415d6599e14cfd93d7fd448648488e";
    }

    //    public static final String TYPE_TEXT = "1_0";
//    public static final String TYPE_IMG = "3_0";
//    public static final String TYPE_VOICE = "34_0";
//    public static final String TYPE_VIDEO = "43_0";
//    public static final String TYPE_EMOJI_PKG = "47_0";
    public static final String TYPE_N = "49_1";
    public static final String TYPE_LINK_CARD1 = "49_4";
    //    public static final String TYPE_LINK_CARD = "49_5";
    public static final String TYPE_FILE = "49_6";
    public static final String TYPE_EMOJI_GIF = "49_8";
    public static final String TYPE_FORWARD_MSGS = "49_19";
    //    public static final String TYPE_MINI_APP = "49_33";
//    public static final String TYPE_MINI_APP2 = "49_36";
    public static final String TYPE_VIDEOS_CHANNEL = "49_51";
    public static final String TYPE_JIELONG = "49_53";
    //    public static final String TYPE_REF = "49_57";
    public static final String TYPE_LIVE = "49_63";
    public static final String TYPE_NOTICE = "49_87";
    public static final String TYPE_LIVE2 = "49_88";
    public static final String TYPE_TRANS = "49_2000";
    public static final String TYPE_RED_PACKET_COVER = "49_2003";
    //    public static final String TYPE_SYS = "10000_0";
    public static final String TYPE_SYS_HIT = "10000_4";
    public static final String TYPE_SYS_CANCEL = "10000_57";
    public static final String TYPE_SYS_INVITE = "10000_8000";

    /*public static final String[] SUPPORT_TYPES = {TYPE_N,
            TYPE_LINK_CARD1,
            TYPE_FILE,
            TYPE_EMOJI_GIF,
            TYPE_FORWARD_MSGS,
            TYPE_VIDEOS_CHANNEL,
            TYPE_JIELONG,
            TYPE_LIVE,
            TYPE_NOTICE,
            TYPE_LIVE2,
            TYPE_TRANS,
            TYPE_RED_PACKET_COVER,
//            TYPE_SYS_HIT,
            TYPE_SYS_CANCEL,
            TYPE_SYS_INVITE};*/

    @Autowired
    private CardLinkMsgStrategy cardLinkMsgStrategy;

    public static int getSize(int size) {
        if (size == 1) {
            return PAGE_SIZE;
        }
        if (size == -1 || size == 0) {
            return -10;
        }
        return size;
    }

    @Override
    public boolean support(Integer type, Integer subType) {
//        return ArrayUtils.contains(SUPPORT_TYPES., type + "_" + subType);
        return SpringUtil.getBeansOfType(MsgStrategy.class)
                .values().stream().noneMatch(x -> !(x instanceof  MyPlugin) && x.support(type, subType));
    }

    @Override
    public void process(MsgVO msgVO) {
        String text;
        String _type = msgVO.getType() + "_" + msgVO.getSubType();
        switch (_type) {
            case TYPE_N -> {
                text = "【消息？】";
//                msg.text += titleMsg(compressContents);
                cardLinkMsgStrategy.process(msgVO);
            }
            case TYPE_LINK_CARD1/*, TYPE_LINK_CARD*/ -> {
                text = "【卡片链接1】";
//                String xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
//                text += cardMsg(xml);
                cardLinkMsgStrategy.process(msgVO);
            }
            case TYPE_FILE -> text = "【文件】";
            case TYPE_EMOJI_GIF -> text = "【GIF表情】";
            case TYPE_FORWARD_MSGS -> {
                text = "【聊天记录】";
//                String xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
//                text += cardMsg(xml);
                cardLinkMsgStrategy.process(msgVO);
            }
            case TYPE_VIDEOS_CHANNEL -> {
                text = "【视频号】";
//                String xml = Jsoup.parseBodyFragment(StringEscapeUtils.unescapeHtml4(uncompress(compressContents)));
//                text += xml.select("nickname").text();
            }
            case TYPE_LIVE -> text = "【直播】";
            case TYPE_NOTICE -> {
                text = "【群公告】";
                StringBuilder _text = new StringBuilder();
                Opt.ofNullable(msgVO.getCompressContent())
                        .map(compressContent -> LZ4Util.decompress(msgVO.getCompressContent()))
//                        .map(xmlContent -> XmlUtil.parseXml(xmlContent, Map.class))
                        .ifPresent(xmlContent -> {
                            try {
                                _text.append(new XmlMapper().readTree(xmlContent).findValue("textannouncement").asText());
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        });
                text += _text.toString();
//                text += xml.select("textannouncement").text();
            }
            case TYPE_LIVE2 -> text = "【直播】";
            case TYPE_TRANS -> text = "【转账】";
            case TYPE_RED_PACKET_COVER -> text = "【红包封面】";
//            case TYPE_SYS -> {
//                text = "【系统通知】";
//                text += msgVO.getStrContent() + StringEscapeUtils.escapeHtml4(LZ4Util.decompress(msgVO.getCompressContent()));
//            }
//            case TYPE_SYS_HIT, TYPE_SYS_CANCEL -> {
//                text = "[拍一拍]";
//                text += strContent;
//                from = null;
//            }
//            case TYPE_SYS_INVITE -> {
//                text = "【邀请入群】";
//                text += strContent;
//            }
            case TYPE_JIELONG -> {
                text = "【接龙】";
//                text += titleMsg(compressContents);
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
//            msgVO.getCardLink().setDes(text + msgVO.getCardLink().getDes());
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

    public static Integer PAGE_SIZE = 500;
    @GetMapping("feed")
    public Object feed(String talker, Long time, Integer size) {
        if (size != null) {
            PAGE_SIZE = size;
            Executors.newScheduledThreadPool(1)
                    .schedule(() -> PAGE_SIZE = 50, 1, TimeUnit.MINUTES);
            return null;
        }

        json.set(talker, time);
        return ResponseEntity.noContent().build();
    }
}
