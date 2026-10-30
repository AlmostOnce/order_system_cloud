package com.hbue.ordering.comment.service.impl;

import com.hbue.ordering.comment.service.CommentService;
import com.hbue.ordering.comment.service.AuthorAvatarService;
import com.hbue.ordering.comment.service.CommentMetadataWriter;
import com.hbue.ordering.comment.service.PurchaseVerifier;
import com.hbue.ordering.comment.repository.CommentContentRepository;

import com.hbue.ordering.common.web.media.FileReferences;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.dto.CreateCommentRequest;
import com.hbue.ordering.comment.model.*;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.vo.*;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CommentServiceImpl implements CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private final CommentMapper rows;
    private final CommentMetadataWriter writer;
    private final PurchaseVerifier purchases;
    private final ManagedImageClient images;
    private final CommentContentRepository contents;
    private final ObjectMapper json;
    private final AuthorAvatarService authors;
    public CommentServiceImpl(CommentMapper rows, CommentMetadataWriter writer, PurchaseVerifier purchases,
            ManagedImageClient images, CommentContentRepository contents, ObjectMapper json, AuthorAvatarService authors) {
        this.rows=rows; this.writer=writer; this.purchases=purchases; this.images=images; this.contents=contents; this.json=json; this.authors=authors;
    }
    public CommentVO create(Long userId, String authorName, String bearer, String key, CreateCommentRequest input) {
        requireUuid(key);
        // 统一归一化后计算幂等摘要；更改内容不能复用原请求键。
        var body = new CreateCommentRequest(input.orderId(), input.productId(), input.score(), input.content() == null ? "" : input.content().trim(),
                (input.imageFileKeys() == null ? List.<String>of() : input.imageFileKeys()).stream().map(k -> k.toLowerCase(Locale.ROOT)).distinct().toList(),
                (input.tags() == null ? List.<String>of() : input.tags()).stream().map(String::trim).distinct().toList());
        if (body.tags().stream().anyMatch(String::isEmpty)) throw bad("评价标签不能为空");
        String hash = hash(body);
        CommentRowDO existing = rows.byRequest(userId, key);
        if (existing != null) return replay(existing, hash);
        Long window = purchases.requireCompletedPurchase(body.orderId(), body.productId(), bearer);
        if (rows.byPurchase(body.orderId(),body.productId()) != null)
            throw new BusinessException(CommonErrorCode.CONFLICT,"该订单的这道菜已经评价");
        List<String> fileKeys = body.imageFileKeys().stream()
                .map(file -> images.requireImageKey(file,userId,"COMMENT_IMAGE",null)).toList();
        String id=UUID.randomUUID().toString();
        String author=authorName==null||authorName.isBlank()?"校园用户":authorName.substring(0,Math.min(64,authorName.length()));
        var row=new CommentRowDO(id,userId,author,body.orderId(),body.productId(),window,body.score(),key,hash,LocalDateTime.now());
        contents.insert(new CommentContentDO(id,body.content(),fileKeys,body.tags(),Instant.now(),false));
        try {
            writer.commit(row);
        } catch (DuplicateKeyException exception) {
            // 不删除Mongo：提交结果不确定时由延迟对账核实，避免误删已经提交的正文。
            existing=rows.byRequest(userId,key);
            if (existing!=null) return replay(existing,hash);
            throw new BusinessException(CommonErrorCode.CONFLICT,"该订单的这道菜已经评价");
        }
        try { contents.markCommitted(id); }
        catch (RuntimeException exception) { log.warn("评论提交后内容标记待对账，commentId={}",id); }
        return view(row);
    }
    public List<CommentVO> forOrder(Long userId, Long orderId) {
        var found = rows.byOrder(userId, orderId);
        if (found.isEmpty()) return List.of();
        var bodies = contents.byIds(found.stream().map(CommentRowDO::commentId).toList());
        var avatars = authors.resolve(List.of(userId));
        return found.stream().map(row -> view(row, bodies.get(row.commentId()), avatars.getOrDefault(userId, ""))).toList();
    }
    public CommentVO detail(String id) { requireUuid(id); return view(requireRow(id)); }
    public CommentPageVO page(Long userId, Long productId, String before, int limit) {
        if (limit<1||limit>50||(productId!=null&&productId<=0)) throw bad("分页参数不正确");
        if (before!=null) requireUuid(before);
        var found=rows.page(userId,productId,before,limit+1);
        boolean more=found.size()>limit;
        var visible=found.stream().limit(limit).toList();
        Map<String,CommentContentDO> contents=new HashMap<>();
        if(!visible.isEmpty()) {
            contents.putAll(this.contents.byIds(visible.stream().map(CommentRowDO::commentId).toList()));
        }
        var avatars=authors.resolve(visible.stream().map(CommentRowDO::userId).toList());
        List<CommentVO> items=visible.stream().map(row->view(row,contents.get(row.commentId()),avatars.getOrDefault(row.userId(),""))).toList();
        return new CommentPageVO(items,more?items.getLast().commentId():null,more);
    }
    private CommentRowDO requireRow(String id) {
        CommentRowDO row=rows.byId(id);
        if(row==null)throw new BusinessException(CommonErrorCode.NOT_FOUND,"评价不存在");
        return row;
    }
    private CommentVO replay(CommentRowDO row,String hash) {
        if(!hash.equals(row.requestHash()))throw new BusinessException(CommonErrorCode.CONFLICT,"请求标识已用于其他评价内容");
        return view(row);
    }
    private CommentVO view(CommentRowDO row) {
        CommentContentDO content=contents.byId(row.commentId());
        return view(row,content,authors.resolve(List.of(row.userId())).getOrDefault(row.userId(),""));
    }
    private CommentVO view(CommentRowDO row,CommentContentDO content,String avatarUrl) {
        if(content==null)throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,"评价内容暂不可用");
        return new CommentVO(row.commentId(),row.productId(),row.windowId(),row.authorName(),avatarUrl,row.score(),
                content.body(),content.imageFileKeys().stream().map(FileReferences::url).toList(),content.tags(),row.createdAt());
    }
    private String hash(CreateCommentRequest body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(body))); }
        catch (java.security.NoSuchAlgorithmException|JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    public static void requireUuid(String id) {
        if(id==null||!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw bad("请求标识格式不正确");
    }
    private static BusinessException bad(String message) {return new BusinessException(CommonErrorCode.BAD_REQUEST,message);}
}
