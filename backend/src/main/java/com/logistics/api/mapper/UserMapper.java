package com.logistics.api.mapper;

import com.logistics.api.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    User selectById(Long id);

    User selectByUsername(String username);

    boolean existsByUsername(String username);

    User selectByPhone(String phone);

    User selectByPhoneAndRole(@Param("phone") String phone, @Param("role") String role);

    boolean existsByPhoneAndRole(@Param("phone") String phone, @Param("role") String role);

    List<User> selectAll();

    long countTotalUsers();

    long countByRoleValue(@Param("role") String role);

    long countUsersCreatedOnOrAfter(@Param("fromTime") LocalDateTime fromTime);

    List<User> selectByRole(@Param("role") String role);

    List<User> selectByRoleAndStatus(@Param("role") String role, @Param("status") Integer status);

    long countAdminListUsers(@Param("keyword") String keyword, @Param("role") String role, @Param("status") String status);

    List<User> selectAdminListUsers(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int insert(User user);

    int updateById(User user);

    int deleteById(Long id);

    // ============================================
    // 复杂查询方法（在XML中实现）
    // ============================================

    /**
     * 分页查询用户列表
     */
    List<User> selectByPage(@Param("role") String role,
                            @Param("status") Integer status,
                            @Param("keyword") String keyword,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    /**
     * 统计用户数量
     */
    long countUsers(@Param("role") String role, @Param("status") Integer status);

    /**
     * 根据ID列表批量查询
     */
    List<User> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 批量更新用户状态
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     * 根据角色统计用户数量
     */
    List<Map<String, Object>> countByRole();

    /**
     * 更新用户状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
