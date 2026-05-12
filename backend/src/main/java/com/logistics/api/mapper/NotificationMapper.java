package com.logistics.api.mapper;

import com.logistics.api.model.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification (user_id, user_type, type, title, content, related_id, related_type) " +
            "VALUES (#{userId}, #{userType}, #{type}, #{title}, #{content}, #{relatedId}, #{relatedType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    @Select("SELECT * FROM notification WHERE id = #{id} AND is_deleted = 0")
    Notification selectById(Long id);

    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND user_type = #{userType} AND is_deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Notification> selectByUser(@Param("userId") Long userId,
                                     @Param("userType") String userType,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    @Select("SELECT * FROM notification WHERE user_id = #{userId} AND user_type = #{userType} AND type = #{type} AND is_deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Notification> selectByUserAndType(@Param("userId") Long userId,
                                            @Param("userType") String userType,
                                            @Param("type") String type,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND user_type = #{userType} AND is_read = 0 AND is_deleted = 0")
    int countUnread(@Param("userId") Long userId, @Param("userType") String userType);

    @Update("UPDATE notification SET is_read = 1, read_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int markAsRead(Long id);

    @Update("UPDATE notification SET is_read = 1, read_time = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId} AND user_type = #{userType} AND is_deleted = 0")
    int markAsReadForOwner(@Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("userType") String userType);

    @Update("UPDATE notification SET is_read = 1, read_time = NOW() " +
            "WHERE user_id = #{userId} AND user_type = #{userType} AND is_read = 0 AND is_deleted = 0")
    int markAllAsRead(@Param("userId") Long userId, @Param("userType") String userType);

    @Update("UPDATE notification SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int deleteById(Long id);

    @Update("UPDATE notification SET is_deleted = 1 " +
            "WHERE id = #{id} AND user_id = #{userId} AND user_type = #{userType} AND is_deleted = 0")
    int deleteByIdForOwner(@Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("userType") String userType);

    @Update("UPDATE notification SET is_deleted = 1 WHERE user_id = #{userId} AND user_type = #{userType} AND is_deleted = 0")
    int deleteByUser(@Param("userId") Long userId, @Param("userType") String userType);
}
