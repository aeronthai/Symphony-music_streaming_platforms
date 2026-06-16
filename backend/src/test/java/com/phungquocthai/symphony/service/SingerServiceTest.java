package com.phungquocthai.symphony.service;

import com.phungquocthai.symphony.constant.ErrorCode;
import com.phungquocthai.symphony.dto.SingerCreateDTO;
import com.phungquocthai.symphony.dto.SingerDTO;
import com.phungquocthai.symphony.dto.SingerUpdateDTO;
import com.phungquocthai.symphony.entity.Singer;
import com.phungquocthai.symphony.entity.User;
import com.phungquocthai.symphony.exception.AppException;
import com.phungquocthai.symphony.mapper.SingerCreateMapper;
import com.phungquocthai.symphony.mapper.SingerMapper;
import com.phungquocthai.symphony.repository.AlbumRepository;
import com.phungquocthai.symphony.repository.SingerRepository;
import com.phungquocthai.symphony.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingerServiceTest {

    @Mock private SingerRepository   singerRepository;
    @Mock private UserRepository     userRepository;
    @Mock private AlbumRepository    albumRepository;
    @Mock private SingerMapper       singerMapper;
    @Mock private SingerCreateMapper singerCreateMapper;
    @Mock private SongService        songService;
    @Mock private ExcelExportUtil    excelExportUtil;

    @InjectMocks
    private SingerService service;

    private Singer entity(int id, String stageName) {
        Singer s = new Singer();
        User u = User.builder()
                .phone("0939020116")
                .avatar("https://symphony/avatar/singer.jpg")
                .gender(1)
                .fullName("Nguyen A")
                .birthday(LocalDate.of(2001,3, 3))
                .role("SINGER")
                .active(true)
                .password("NA@123")
                .create_at(LocalDateTime.now())
                .build();
        s.setSinger_id(id);
        s.setStageName(stageName);
        s.setFollowers(0);
        s.setUser(u);
        return s;
    }

    private SingerDTO dto(int id, String stageName) {
        SingerDTO d = new SingerDTO();
        d.setSinger_id(id);
        d.setStageName(stageName);
        return d;
    }

    private User userWithRole(String role) {
        User u = new User();
        u.setRole(role);
        return u;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("lưu singer và trả về DTO đúng")
        void success() {
            SingerCreateDTO createDTO = new SingerCreateDTO();
            Singer          saved     = entity(1, "Sơn Tùng");
            SingerDTO       expected  = dto(1, "Sơn Tùng");

            when(singerCreateMapper.toEntity(createDTO)).thenReturn(saved);
            when(singerRepository.save(saved)).thenReturn(saved);
            when(singerMapper.toDTO(saved)).thenReturn(expected);

            SingerDTO result = service.create(createDTO);

            assertThat(result.getSinger_id()).isEqualTo(1);
            assertThat(result.getStageName()).isEqualTo("Sơn Tùng");
            verify(singerRepository).save(saved);
        }

        @Test
        @DisplayName("mapper được gọi đúng thứ tự: createMapper → save → mapper")
        void mapperCallOrder() {
            SingerCreateDTO createDTO = new SingerCreateDTO();
            Singer          saved     = entity(2, "Mỹ Tâm");

            when(singerCreateMapper.toEntity(createDTO)).thenReturn(saved);
            when(singerRepository.save(saved)).thenReturn(saved);
            when(singerMapper.toDTO(saved)).thenReturn(dto(2, "Mỹ Tâm"));

            service.create(createDTO);

            var inOrder = inOrder(singerCreateMapper, singerRepository, singerMapper);
            inOrder.verify(singerCreateMapper).toEntity(createDTO);
            inOrder.verify(singerRepository).save(saved);
            inOrder.verify(singerMapper).toDTO(saved);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("cập nhật stageName thành công")
        void success() {
            Singer singer = entity(2, "Tên cũ");

            SingerUpdateDTO updateDTO = new SingerUpdateDTO();
            updateDTO.setSinger_id(2);
            updateDTO.setStageName("Tên mới");

            when(singerRepository.findById(2)).thenReturn(Optional.of(singer));
            when(singerRepository.save(singer)).thenReturn(singer);
            when(singerMapper.toDTO(singer)).thenReturn(dto(2, "Tên mới"));

            SingerDTO result = service.update(updateDTO);

            assertThat(result.getStageName()).isEqualTo("Tên mới");
            // entity bị mutate trực tiếp
            assertThat(singer.getStageName()).isEqualTo("Tên mới");
        }

        @Test
        @DisplayName("ném AppException khi singer không tồn tại")
        void notFound() {
            SingerUpdateDTO updateDTO = new SingerUpdateDTO();
            updateDTO.setSinger_id(999);

            when(singerRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(updateDTO))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SINGER_NOT_EXISTED);
        }

        @Test
        @DisplayName("không gọi save khi findById ném exception")
        void doesNotSaveOnException() {
            SingerUpdateDTO updateDTO = new SingerUpdateDTO();
            updateDTO.setSinger_id(404);

            when(singerRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(updateDTO))
                    .isInstanceOf(AppException.class);

            verify(singerRepository, never()).save(any());
        }

        @Test
        @DisplayName("update với stageName rỗng vẫn save (validation ở controller)")
        void updateWithBlankStageName_stillSaves() {
            Singer singer = entity(3, "Tên cũ");

            SingerUpdateDTO updateDTO = new SingerUpdateDTO();
            updateDTO.setSinger_id(3);
            updateDTO.setStageName("");

            when(singerRepository.findById(3)).thenReturn(Optional.of(singer));
            when(singerRepository.save(singer)).thenReturn(singer);
            when(singerMapper.toDTO(singer)).thenReturn(dto(3, ""));

            service.update(updateDTO);

            assertThat(singer.getStageName()).isEqualTo("");
            verify(singerRepository).save(singer);
        }
    }


    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("xóa nhiều bài hát, album, hạ role USER, disable singer")
        void fullCleanup_withMultipleSongs() {
            User user = userWithRole("SINGER");

            when(singerRepository.findSongIdsInPresentBySingerId(3)).thenReturn(List.of(10, 11, 12));
            when(userRepository.findBySingerId(3)).thenReturn(Optional.of(user));

            service.delete(3);

            verify(songService).delete(3, 10);
            verify(songService).delete(3, 11);
            verify(songService).delete(3, 12);
            verify(albumRepository).deleteAlbumsBySingerId(3);
            assertThat(user.getRole()).isEqualTo("USER");
            verify(userRepository).save(user);
            verify(singerRepository).updateIsActive(3, false);
        }

        @Test
        @DisplayName("không gọi songService khi singer không có bài hát nào")
        void noSongs_skipsSongDelete() {
            User user = userWithRole("SINGER");

            when(singerRepository.findSongIdsInPresentBySingerId(4)).thenReturn(List.of());
            when(userRepository.findBySingerId(4)).thenReturn(Optional.of(user));

            service.delete(4);

            verify(songService, never()).delete(any(), any());
            verify(singerRepository).updateIsActive(4, false);
        }

        @Test
        @DisplayName("xóa đúng 1 lần khi singer chỉ có 1 bài")
        void oneSong_callsDeleteOnce() {
            User user = userWithRole("SINGER");

            when(singerRepository.findSongIdsInPresentBySingerId(5)).thenReturn(List.of(99));
            when(userRepository.findBySingerId(5)).thenReturn(Optional.of(user));

            service.delete(5);

            verify(songService, times(1)).delete(5, 99);
        }

        @Test
        @DisplayName("album luôn bị xóa dù singer có bài hay không")
        void albumAlwaysDeleted() {
            User user = userWithRole("SINGER");

            when(singerRepository.findSongIdsInPresentBySingerId(6)).thenReturn(List.of());
            when(userRepository.findBySingerId(6)).thenReturn(Optional.of(user));

            service.delete(6);

            verify(albumRepository).deleteAlbumsBySingerId(6);
        }

        @Test
        @DisplayName("ném exception khi user không tồn tại, singer không bị disable")
        void throwsWhenUserNotFound_singerNotDisabled() {
            when(singerRepository.findSongIdsInPresentBySingerId(7)).thenReturn(List.of());
            when(userRepository.findBySingerId(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(7))
                    .isInstanceOf(java.util.NoSuchElementException.class);

            verify(singerRepository, never()).updateIsActive(any(), anyBoolean());
        }

        @Test
        @DisplayName("songService.delete được gọi đúng singerId cho mỗi bài")
        void correctSingerIdPassedToSongDelete() {
            User user = userWithRole("SINGER");

            when(singerRepository.findSongIdsInPresentBySingerId(20)).thenReturn(List.of(101, 102));
            when(userRepository.findBySingerId(20)).thenReturn(Optional.of(user));

            service.delete(20);

            // đảm bảo singerId không bị nhầm
            verify(songService).delete(eq(20), eq(101));
            verify(songService).delete(eq(20), eq(102));
            verify(songService, never()).delete(eq(101), any());
        }
    }

    @Nested
    @DisplayName("enable()")
    class Enable {

        @Test
        @DisplayName("nâng role lên SINGER và kích hoạt singer")
        void promotesAndActivates() {
            User user = userWithRole("USER");
            when(userRepository.findBySingerId(5)).thenReturn(Optional.of(user));

            service.enable(5);

            assertThat(user.getRole()).isEqualTo("SINGER");
            verify(userRepository).save(user);
            verify(singerRepository).updateIsActive(5, true);
        }

        @Test
        @DisplayName("enable sau delete → role trở lại SINGER")
        void enableAfterDelete_roleRestored() {
            User user = userWithRole("USER");
            when(userRepository.findBySingerId(8)).thenReturn(Optional.of(user));

            service.enable(8);

            assertThat(user.getRole()).isEqualTo("SINGER");
            verify(singerRepository).updateIsActive(8, true);
        }

        @Test
        @DisplayName("ném exception khi user không tồn tại, singer không được activate")
        void throwsWhenUserNotFound_singerNotActivated() {
            when(userRepository.findBySingerId(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enable(999))
                    .isInstanceOf(java.util.NoSuchElementException.class);

            verify(singerRepository, never()).updateIsActive(any(), anyBoolean());
        }

        @Test
        @DisplayName("gọi updateIsActive(true) chứ không phải false")
        void activatesNotDeactivates() {
            User user = userWithRole("USER");
            when(userRepository.findBySingerId(6)).thenReturn(Optional.of(user));

            service.enable(6);

            verify(singerRepository).updateIsActive(6, true);
            verify(singerRepository, never()).updateIsActive(6, false);
        }
    }

    @Nested
    @DisplayName("getSinger()")
    class GetSinger {

        @Test
        @DisplayName("trả về DTO khi singer active tồn tại")
        void success() {
            Singer singer = entity(6, "Mỹ Tâm");
            when(singerRepository.findSingerActive(6)).thenReturn(Optional.of(singer));
            when(singerMapper.toDTO(singer)).thenReturn(dto(6, "Mỹ Tâm"));

            SingerDTO result = service.getSinger(6);

            assertThat(result.getStageName()).isEqualTo("Mỹ Tâm");
        }

        @Test
        @DisplayName("ném AppException khi singer không active / không tồn tại")
        void notActive_throws() {
            when(singerRepository.findSingerActive(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSinger(99))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SINGER_NOT_EXISTED);
        }

        @Test
        @DisplayName("không gọi singerMapper khi không tìm thấy singer")
        void noMapperCallOnNotFound() {
            when(singerRepository.findSingerActive(88)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSinger(88))
                    .isInstanceOf(AppException.class);

            verifyNoInteractions(singerMapper);
        }
    }

    @Nested
    @DisplayName("getSingerByUserId()")
    class GetSingerByUserId {

        @Test
        @DisplayName("trả về DTO đúng khi tìm theo userId")
        void success() {
            Singer singer = entity(10, "Hoàng Thùy Linh");
            when(singerRepository.findByUserId(50)).thenReturn(Optional.of(singer));
            when(singerMapper.toDTO(singer)).thenReturn(dto(10, "Hoàng Thùy Linh"));

            SingerDTO result = service.getSingerByUserId(50);

            assertThat(result.getStageName()).isEqualTo("Hoàng Thùy Linh");
        }

        @Test
        @DisplayName("ném AppException khi không tìm thấy theo userId")
        void notFound_throws() {
            when(singerRepository.findByUserId(77)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSingerByUserId(77))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SINGER_NOT_EXISTED);
        }

        @Test
        @DisplayName("không gọi singerMapper khi không tìm thấy theo userId")
        void noMapperCallOnNotFound() {
            when(singerRepository.findByUserId(55)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSingerByUserId(55))
                    .isInstanceOf(AppException.class);

            verifyNoInteractions(singerMapper);
        }
    }
}