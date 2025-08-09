package com.xcs.wx.msg.impl;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.util.ReUtil;
import com.xcs.wx.domain.vo.MsgVO;
import com.xcs.wx.msg.MsgStrategy;
import org.springframework.stereotype.Service;

/**
 * 视频消息
 *
 * @author xcs
 * @date 2024年01月24日 13时52分
 **/
@Service
public class VideoMsgStrategy implements MsgStrategy {

    @Override
    public boolean support(Integer type, Integer subType) {
        return type == 43 && subType == 0;
    }

    @Override
    public void process(MsgVO msgVO) {
        Opt.ofNullable(msgVO.getBytesExtra())
                .map(xmlContent -> new String(msgVO.getBytesExtra()))
                .ifPresent(extra -> {
                    String thumb = ReUtil.getGroup0("FileStorage\\\\Video\\\\[^\\\\]+\\\\[^\\\\]+\\.jpg", extra);
                    msgVO.setThumb(thumb);
                    msgVO.setStrContent("[视频]");
                });
    }
}
