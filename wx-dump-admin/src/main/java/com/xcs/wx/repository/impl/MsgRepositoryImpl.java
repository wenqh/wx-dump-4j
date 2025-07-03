package com.xcs.wx.repository.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xcs.wx.constant.DataSourceType;
import com.xcs.wx.domain.Msg;
import com.xcs.wx.domain.vo.CountRecentMsgsVO;
import com.xcs.wx.domain.vo.MsgTypeDistributionVO;
import com.xcs.wx.domain.vo.TopContactsVO;
import com.xcs.wx.mapper.MsgMapper;
import com.xcs.wx.repository.MsgRepository;
import com.xcs.wx.service.impl.MyPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息 Repository 实现类
 *
 * @author xcs
 * @date 2023年12月25日 15时29分
 **/
@Slf4j
@Repository
@RequiredArgsConstructor
public class MsgRepositoryImpl extends ServiceImpl<MsgMapper, Msg> implements MsgRepository {

    @Override
    public List<Msg> queryMsgByTalker(String talker, Long nextSequence, int size) {
        List<Msg> msgList = new ArrayList<>();
        List<String> msgDbList = DataSourceType.getMsgDb().stream()
                .sorted(Comparator.comparingInt(s -> (size > 0 ? 1 : -1) * Integer.parseInt(s.replaceAll(".*#MSG(\\d+)\\.db", "$1"))))
                .toList();
        int offset = Math.abs(size);
        for (String poolName : msgDbList) {
            if (offset <= 0) break;
            DynamicDataSourceContextHolder.push(poolName);
            List<Msg> queryResultList = super.list(Wrappers.<Msg>lambdaQuery()
                    .eq(Msg::getStrTalker, talker).orderBy(true, size >= 0, Msg::getSequence)
                    .le(size < 0 && (nextSequence != null && nextSequence > 0), Msg::getSequence, nextSequence)
                    .gt(size >= 0 && (nextSequence != null && nextSequence > 0), Msg::getSequence, nextSequence)
                    .last("limit " + offset));
            DynamicDataSourceContextHolder.clear();
            offset -= queryResultList.size();
            msgList.addAll(queryResultList);
        }
        return msgList;
    }

    @Override
    public List<Msg> exportMsg(String talker) {
        List<Msg> msgList = new ArrayList<>();
        List<String> msgDbList = DataSourceType.getMsgDb();
        for (String poolName : msgDbList) {
            DynamicDataSourceContextHolder.push(poolName);
            List<Msg> queryResultList = super.list(Wrappers.<Msg>lambdaQuery()
                    .eq(Msg::getStrTalker, talker)
                    .orderByDesc(Msg::getSequence));
            DynamicDataSourceContextHolder.clear();
            msgList.addAll(queryResultList);
        }
        return msgList;
    }

    @Override
    public List<MsgTypeDistributionVO> msgTypeDistribution() {
        Optional<String> poolNameOptional = DataSourceType.getMsgDb().stream().max(Comparator.naturalOrder());
        if (poolNameOptional.isPresent()) {
            DynamicDataSourceContextHolder.push(poolNameOptional.get());
            List<MsgTypeDistributionVO> currentMsgsList = super.getBaseMapper().msgTypeDistribution();
            DynamicDataSourceContextHolder.clear();
            return currentMsgsList;
        }
        return Collections.emptyList();
    }

    @Override
    public List<CountRecentMsgsVO> countRecentMsgs() {
        Optional<String> poolNameOptional = DataSourceType.getMsgDb().stream().max(Comparator.naturalOrder());
        if (poolNameOptional.isPresent()) {
            DynamicDataSourceContextHolder.push(poolNameOptional.get());
            List<CountRecentMsgsVO> currentMsgsList = super.getBaseMapper().countRecentMsgs();
            DynamicDataSourceContextHolder.clear();
            return currentMsgsList.stream()
                    .sorted(Comparator.comparing(CountRecentMsgsVO::getType).reversed())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public List<TopContactsVO> topContacts() {
        Optional<String> poolNameOptional = DataSourceType.getMsgDb().stream().max(Comparator.naturalOrder());
        if (poolNameOptional.isPresent()) {
            DynamicDataSourceContextHolder.push(poolNameOptional.get());
            List<TopContactsVO> currentContactsList = super.getBaseMapper().topContacts();
            DynamicDataSourceContextHolder.clear();

            return currentContactsList.stream()
                    .sorted(Comparator.comparing(TopContactsVO::getTotal).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public int countSent() {
        Optional<String> poolNameOptional = DataSourceType.getMsgDb().stream().max(Comparator.naturalOrder());
        if (poolNameOptional.isPresent()) {
            DynamicDataSourceContextHolder.push(poolNameOptional.get());
            int count = getBaseMapper().countSent();
            DynamicDataSourceContextHolder.clear();
            return count;
        }
        return 0;
    }

    @Override
    public int countReceived() {
        Optional<String> poolNameOptional = DataSourceType.getMsgDb().stream().max(Comparator.naturalOrder());
        if (poolNameOptional.isPresent()) {
            DynamicDataSourceContextHolder.push(poolNameOptional.get());
            int count = getBaseMapper().countReceived();
            DynamicDataSourceContextHolder.clear();
            return count;
        }
        return 0;
    }
}
