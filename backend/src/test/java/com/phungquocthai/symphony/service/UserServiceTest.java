package com.phungquocthai.symphony.service;

import com.phungquocthai.symphony.constant.ErrorCode;
import com.phungquocthai.symphony.constant.PathStorage;
import com.phungquocthai.symphony.dto.UserDTO;
import com.phungquocthai.symphony.dto.UserRegistrationDTO;
import com.phungquocthai.symphony.dto.UserUpdateDTO;
import com.phungquocthai.symphony.entity.User;
import com.phungquocthai.symphony.exception.AppException;
import com.phungquocthai.symphony.mapper.UserMapper;
import com.phungquocthai.symphony.mapper.UserRegistrationMapper;
import com.phungquocthai.symphony.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository         userRepository;
    @Mock private FileStorageService     fileStorageService;
    @Mock private UserMapper             userMapper;
    @Mock private UserRegistrationMapper userRegistrationMapper;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private VipRepository          vipRepository;
    @Mock private PlaylistRepository     playlistRepository;
    @Mock private FavoriteRepository     favoriteRepository;
    @Mock private ListenRepository       listenRepository;
    @Mock private SingerRepository       singerRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private ExcelExportUtil        excelExportUtil;

    @InjectMocks
    private UserService service;

    private User userEntity(int id, String phone, String hashedPassword) {
        User u = new User();
        u.setUserId(id);
        u.setPhone(phone);
        u.setPassword(hashedPassword);
        u.setFullName("Test User");
        return u;
    }

    private UserDTO userDTO(int id) {
        UserDTO dto = new UserDTO();
        dto.setUserId(id);
        return dto;
    }

    private UserUpdateDTO updateDTO(int id) {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setId(id);
        dto.setFullName("Updated Name");
        dto.setPhone("0900000000");
        return dto;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("đăng ký thành công, role=USER, avatar mặc định")
        void success() {
            UserRegistrationDTO dto    = new UserRegistrationDTO();
            dto.setPhone("0901234567");
            dto.setPassword("plaintext");

            User entity = userEntity(1, "0901234567", "encoded");

            when(userRepository.existsByPhone("0901234567")).thenReturn(false);
            when(passwordEncoder.encode("plaintext")).thenReturn("encoded");
            when(userRegistrationMapper.toEntity(dto)).thenReturn(entity);
            when(userMapper.toDTO(entity)).thenReturn(userDTO(1));

            service.create(dto);

            assertThat(entity.getRole()).isEqualTo("USER");
            assertThat(entity.getAvatar()).isEqualTo("/images/avatars/default-avatar.jpg");
            verify(userRepository).save(entity);
        }

        @Test
        @DisplayName("ném AppException khi phone đã tồn tại")
        void duplicatePhone_throws() {
            UserRegistrationDTO dto = new UserRegistrationDTO();
            dto.setPhone("0901234567");

            when(userRepository.existsByPhone("0901234567")).thenReturn(true);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_EXISTED);
        }

        @Test
        @DisplayName("không gọi save khi phone đã tồn tại")
        void duplicatePhone_noSave() {
            UserRegistrationDTO dto = new UserRegistrationDTO();
            dto.setPhone("0901111111");

            when(userRepository.existsByPhone("0901111111")).thenReturn(true);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(AppException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("password được encode trước khi lưu")
        void passwordEncoded_beforeSave() {
            UserRegistrationDTO dto = new UserRegistrationDTO();
            dto.setPhone("0902222222");
            dto.setPassword("raw");

            User entity = userEntity(2, "0902222222", "hashed");

            when(userRepository.existsByPhone("0902222222")).thenReturn(false);
            when(passwordEncoder.encode("raw")).thenReturn("hashed");
            when(userRegistrationMapper.toEntity(dto)).thenReturn(entity);
            when(userMapper.toDTO(entity)).thenReturn(userDTO(2));

            service.create(dto);

            // encode phải được gọi trước khi mapper nhận dto
            var inOrder = inOrder(passwordEncoder, userRegistrationMapper, userRepository);
            inOrder.verify(passwordEncoder).encode("raw");
            inOrder.verify(userRegistrationMapper).toEntity(dto);
            inOrder.verify(userRepository).save(entity);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("lưu user thành công khi password đúng, không đổi avatar")
        void success_noAvatarChange() {
            User entity = userEntity(3, "0923456789", "hashed");
            UserUpdateDTO dto = updateDTO(3);

            when(userRepository.findById(3)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

            service.update(dto, null, "correct", "", "");

            assertThat(entity.getFullName()).isEqualTo("Updated Name");
            verify(userRepository).save(entity);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("lưu user thành công khi password đúng và đổi avatar")
        void success_withAvatarChange() {
            User entity = userEntity(3, "0923456789", "hashed");
            UserUpdateDTO dto = updateDTO(3);
            MultipartFile avatarFile = mock(MultipartFile.class);

            when(userRepository.findById(3)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
            when(fileStorageService.storeFile(avatarFile, PathStorage.AVATAR)).thenReturn("avatars/new.jpg");

            service.update(dto, avatarFile, "correct", "", "");

            assertThat(entity.getAvatar()).isEqualTo("avatars/new.jpg");
            verify(userRepository).save(entity);
        }

        @Test
        @DisplayName("không lưu khi password hiện tại sai")
        void wrongPassword_noSave() {
            User entity = userEntity(2, "0912345678", "hashed");
            UserUpdateDTO dto = updateDTO(2);

            when(userRepository.findById(2)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            service.update(dto, null, "wrong", "", "");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("không lưu khi newPassword != password_confirm")
        void passwordConfirmMismatch_noDbQuery() {
            // return sớm trước khi query DB
            service.update(updateDTO(4), null, "any", "abc", "xyz");

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("ném AppException khi user không tồn tại")
        void userNotFound_throws() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(updateDTO(99), null, "pass", "", ""))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_EXISRED);
        }

        @Test
        @DisplayName("không đổi password khi newPassword rỗng")
        void emptyNewPassword_passwordUnchanged() {
            User entity = userEntity(5, "0944444444", "original_hash");
            UserUpdateDTO dto = updateDTO(5);

            when(userRepository.findById(5)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("correct", "original_hash")).thenReturn(true);

            service.update(dto, null, "correct", "", "");

            // password không bị encode lại
            verify(passwordEncoder, never()).encode(any());
            assertThat(entity.getPassword()).isEqualTo("original_hash");
        }

        @Test
        @DisplayName("đổi password thành công khi newPassword == password_confirm")
        void changePassword_success() {
            User entity = userEntity(6, "0955555555", "old_hash");
            UserUpdateDTO dto = updateDTO(6);

            when(userRepository.findById(6)).thenReturn(Optional.of(entity));
            when(passwordEncoder.matches("current", "old_hash")).thenReturn(true);
            when(passwordEncoder.encode("newPass123")).thenReturn("new_hash");

            service.update(dto, null, "current", "newPass123", "newPass123");

            assertThat(entity.getPassword()).isEqualTo("new_hash");
            verify(userRepository).save(entity);
        }
    }

    @Nested
    @DisplayName("updateByAdmin()")
    class UpdateByAdmin {

        @Test
        @DisplayName("cập nhật thông tin user không cần password")
        void success_noPassword() {
            User entity = userEntity(5, "0934567890", "hashed");
            UserUpdateDTO dto = updateDTO(5);

            when(userRepository.findById(5)).thenReturn(Optional.of(entity));

            service.updateByAdmin(dto, null);

            assertThat(entity.getFullName()).isEqualTo("Updated Name");
            verify(userRepository).save(entity);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("cập nhật avatar khi có file")
        void success_withAvatar() {
            User entity = userEntity(6, "0934567891", "hashed");
            UserUpdateDTO dto = updateDTO(6);
            MultipartFile avatarFile = mock(MultipartFile.class);

            when(userRepository.findById(6)).thenReturn(Optional.of(entity));
            when(fileStorageService.storeFile(avatarFile, PathStorage.AVATAR)).thenReturn("avatars/admin_set.jpg");

            service.updateByAdmin(dto, avatarFile);

            assertThat(entity.getAvatar()).isEqualTo("avatars/admin_set.jpg");
        }

        @Test
        @DisplayName("ném AppException khi user không tồn tại")
        void userNotFound_throws() {
            when(userRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateByAdmin(updateDTO(999), null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_EXISRED);
        }
    }

    @Nested
    @DisplayName("delete() / enable()")
    class DeleteEnable {

        @Test
        @DisplayName("delete() – chỉ disable user, không xóa vật lý")
        void delete_disablesOnly() {
            service.delete(7);

            verify(userRepository).updateIsActive(7, false);
            verify(userRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("delete() – không ảnh hưởng user khác")
        void delete_onlyTargetUser() {
            service.delete(7);

            verify(userRepository).updateIsActive(eq(7), eq(false));
            verify(userRepository, times(1)).updateIsActive(any(), anyBoolean());
        }

        @Test
        @DisplayName("enable() – kích hoạt lại user")
        void enable_activates() {
            service.enable(8);

            verify(userRepository).updateIsActive(8, true);
        }

        @Test
        @DisplayName("enable() – gọi updateIsActive(true) chứ không phải false")
        void enable_trueNotFalse() {
            service.enable(9);

            verify(userRepository).updateIsActive(9, true);
            verify(userRepository, never()).updateIsActive(9, false);
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("trả về DTO khi tìm thấy")
        void success() {
            User entity = userEntity(10, "0900000001", "hashed");
            when(userRepository.findById(10)).thenReturn(Optional.of(entity));
            when(userMapper.toDTO(entity)).thenReturn(userDTO(10));

            UserDTO result = service.getUserById(10);

            assertThat(result.getUserId()).isEqualTo(10);
        }

        @Test
        @DisplayName("ném AppException khi user không tồn tại")
        void notFound_throws() {
            when(userRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserById(999))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_EXISRED);
        }

        @Test
        @DisplayName("không gọi userMapper khi không tìm thấy user")
        void noMapperCallOnNotFound() {
            when(userRepository.findById(888)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserById(888))
                    .isInstanceOf(AppException.class);

            verifyNoInteractions(userMapper);
        }
    }

    @Nested
    @DisplayName("getUserBySingerId()")
    class GetUserBySingerId {

        @Test
        @DisplayName("trả về DTO khi tìm thấy")
        void success() {
            User entity = userEntity(11, "0900000002", "hashed");
            when(userRepository.findBySingerId(20)).thenReturn(Optional.of(entity));
            when(userMapper.toDTO(entity)).thenReturn(userDTO(11));

            UserDTO result = service.getUserBySingerId(20);

            assertThat(result.getUserId()).isEqualTo(11);
        }

        @Test
        @DisplayName("ném AppException khi không tìm thấy theo singerId")
        void notFound_throws() {
            when(userRepository.findBySingerId(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserBySingerId(999))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_EXISRED);
        }
    }
}