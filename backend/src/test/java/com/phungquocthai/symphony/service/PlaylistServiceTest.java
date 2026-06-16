package com.phungquocthai.symphony.service;

import com.phungquocthai.symphony.dto.PlaylistDTO;
import com.phungquocthai.symphony.entity.Playlist;
import com.phungquocthai.symphony.entity.User;
import com.phungquocthai.symphony.repository.PlaylistRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock private PlaylistRepository playlistRepository;
    @Mock private UserRepository     userRepository;

    @InjectMocks
    private PlaylistService service;


    private User user(int id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Aeron Summer");
        u.setPassword("leak0@:>>");
        u.setGender(1);
        u.setRole("ADMIN");
        return u;
    }

    private Playlist playlist(int id, String name, User user) {
        return Playlist.builder()
                .playlist_id(id)
                .playlist_name(name)
                .user(user)
                .create_at(LocalDate.of(2024, 1, 1))
                .build();
    }

    @Nested
    @DisplayName("getPlaylistOfUser()")
    class GetPlaylistOfUser {

        @Test
        @DisplayName("trả về danh sách DTO khi user có playlist")
        void success_multiplePlaylists() {
            User u = user(1);
            List<Playlist> entities = List.of(
                    playlist(1, "Chill",    u),
                    playlist(2, "Workout",  u),
                    playlist(3, "Morning",  u)
            );
            when(playlistRepository.getByUserId(1)).thenReturn(entities);

            List<PlaylistDTO> result = service.getPlaylistOfUser(1);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getPlaylistName()).isEqualTo("Chill");
            assertThat(result.get(2).getPlaylistName()).isEqualTo("Morning");
        }

        @Test
        @DisplayName("trả về list rỗng khi user không có playlist")
        void noPlaylists_returnsEmpty() {
            when(playlistRepository.getByUserId(99)).thenReturn(List.of());

            assertThat(service.getPlaylistOfUser(99)).isEmpty();
        }
    }

    @Nested
    @DisplayName("createPlaylist()")
    class CreatePlaylist {

        @Test
        @DisplayName("tạo playlist thành công, trả về DTO đúng")
        void success() {
            User u = user(1);
            Playlist saved = playlist(10, "My Mix", u);

            PlaylistDTO dto = PlaylistDTO.builder().playlistName("My Mix").build();

            when(userRepository.findById(1)).thenReturn(Optional.of(u));
            when(playlistRepository.save(any(Playlist.class))).thenReturn(saved);

            PlaylistDTO result = service.createPlaylist(dto, 1);

            assertThat(result.getPlaylistId()).isEqualTo(10);
            assertThat(result.getPlaylistName()).isEqualTo("My Mix");
        }

        @Test
        @DisplayName("ném RuntimeException khi user không tồn tại")
        void userNotFound_throws() {
            PlaylistDTO dto = PlaylistDTO.builder().playlistName("Test").build();
            when(userRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPlaylist(dto, 999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("không gọi playlistRepository.save khi user không tồn tại")
        void userNotFound_noSave() {
            PlaylistDTO dto = PlaylistDTO.builder().playlistName("Test").build();
            when(userRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPlaylist(dto, 999))
                    .isInstanceOf(RuntimeException.class);

            verify(playlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("playlist được gán đúng user")
        void savesCorrectUser() {
            User u = user(7);
            Playlist saved = playlist(11, "Vibes", u);
            PlaylistDTO dto = PlaylistDTO.builder().playlistName("Vibes").build();

            when(userRepository.findById(7)).thenReturn(Optional.of(u));
            when(playlistRepository.save(any())).thenReturn(saved);

            service.createPlaylist(dto, 7);

            verify(playlistRepository).save(argThat(p -> p.getUser().getUserId() == 7));
        }

        @Test
        @DisplayName("DTO kết quả chứa createAt từ entity")
        void resultContainsCreateAt() {
            User u = user(1);
            Playlist saved = playlist(12, "Daily", u);
            PlaylistDTO dto = PlaylistDTO.builder().playlistName("Daily").build();

            when(userRepository.findById(1)).thenReturn(Optional.of(u));
            when(playlistRepository.save(any())).thenReturn(saved);

            PlaylistDTO result = service.createPlaylist(dto, 1);

            assertThat(result.getCreateAt())
                    .isEqualTo(LocalDate.of(2024, 1, 1));
        }
    }

    @Nested
    @DisplayName("update()")
    class UpdatePlaylist {

        @Test
        @DisplayName("cập nhật tên playlist thành công")
        void success() {
            User u = user(1);
            Playlist entity = playlist(1, "Old Name", u);

            when(playlistRepository.findById(1)).thenReturn(Optional.of(entity));

            service.update(1, "New Name");

            assertThat(entity.getPlaylist_name()).isEqualTo("New Name");
            verify(playlistRepository).save(entity);
        }

        @Test
        @DisplayName("ném exception khi playlist không tồn tại")
        void notFound_throws() {
            when(playlistRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(999, "New Name"))
                    .isInstanceOf(java.util.NoSuchElementException.class);

            verify(playlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("cập nhật với tên rỗng vẫn save (validate ở controller)")
        void emptyName_stillSaves() {
            User u = user(1);
            Playlist entity = playlist(2, "Old", u);

            when(playlistRepository.findById(2)).thenReturn(Optional.of(entity));

            service.update(2, "");

            assertThat(entity.getPlaylist_name()).isEqualTo("");
            verify(playlistRepository).save(entity);
        }
    }

    @Nested
    @DisplayName("deletePlaylistWithSongs()")
    class DeletePlaylist {

        @Test
        @DisplayName("delegate đúng tới repository.delete_playlist()")
        void delegatesToRepo() {
            service.deletePlaylistWithSongs(5);
            verify(playlistRepository).delete_playlist(5);
        }

        @Test
        @DisplayName("không gọi playlistRepository.deleteById")
        void doesNotCallDeleteById() {
            service.deletePlaylistWithSongs(5);
            verify(playlistRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("isSongInPlaylist()")
    class IsSongInPlaylist {

        @Test
        @DisplayName("trả về true khi bài hát có trong playlist")
        void songInPlaylist_returnsTrue() {
            when(playlistRepository.isSongInPlaylist(1, 10)).thenReturn(10);
            assertThat(service.isSongInPlaylist(1, 10)).isTrue();
        }

        @Test
        @DisplayName("trả về false khi bài hát không có trong playlist")
        void songNotInPlaylist_returnsFalse() {
            when(playlistRepository.isSongInPlaylist(1, 99)).thenReturn(null);
            assertThat(service.isSongInPlaylist(1, 99)).isFalse();
        }
    }

    @Nested
    @DisplayName("addSongToPlaylist() / removeSongFromPlaylist()")
    class SongPlaylistLink {

        @Test
        @DisplayName("addSongToPlaylist() – delegate đúng tới repository")
        void add_delegatesToRepo() {
            service.addSongToPlaylist(1, 10);
            verify(playlistRepository).addSongToPlaylist(1, 10);
        }

        @Test
        @DisplayName("removeSongFromPlaylist() – delegate đúng tới repository")
        void remove_delegatesToRepo() {
            service.removeSongFromPlaylist(1, 10);
            verify(playlistRepository).removeSongFromPlaylist(1, 10);
        }

        @Test
        @DisplayName("add không gọi remove và ngược lại")
        void noSideEffects() {
            service.addSongToPlaylist(3, 30);
            verify(playlistRepository, never()).removeSongFromPlaylist(any(), any());

            service.removeSongFromPlaylist(3, 30);
            verify(playlistRepository, times(1)).addSongToPlaylist(3, 30);
        }
    }
}