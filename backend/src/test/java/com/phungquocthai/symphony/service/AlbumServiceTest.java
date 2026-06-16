package com.phungquocthai.symphony.service;

import com.phungquocthai.symphony.constant.PathStorage;
import com.phungquocthai.symphony.dto.AlbumDTO;
import com.phungquocthai.symphony.entity.Album;
import com.phungquocthai.symphony.entity.Singer;
import com.phungquocthai.symphony.entity.Song;
import com.phungquocthai.symphony.repository.AlbumRepository;
import com.phungquocthai.symphony.repository.SingerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock private AlbumRepository    albumRepository;
    @Mock private SingerRepository   singerRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private AlbumService service;

    private Singer singer(int id) {
        Singer s = new Singer();
        s.setSinger_id(id);
        s.setStageName("Singer " + id);
        return s;
    }

    private Album album(int id, String name, String img, Singer singer) {
        return Album.builder()
                .albumId(id)
                .albumName(name)
                .albumImg(img)
                .singer(singer)
                .songs(Set.of(
                        Song.builder()
                                .song_id(1)
                                .isVip(false)
                                .songName("We don't talk anymore")
                                .total_listens(0)
                                .author("Charlie Puth")
                                .build(),
                        Song.builder()
                                .song_id(2)
                                .isVip(true)
                                .songName("Mượn rượu tỏ tình")
                                .author("BigDaddy")
                                .build()))
                .build();
    }

    @Nested
    @DisplayName("getAlbumsOfSinger()")
    class GetAlbumsOfSinger {

        @Test
        @DisplayName("trả về danh sách DTO khi singer có album")
        void success_multipleAlbums() {
            Singer s = singer(1);
            List<Album> albums = List.of(
                    album(1, "Album A", "img/a.jpg", s),
                    album(2, "Album B", "img/b.jpg", s)
            );
            when(albumRepository.getBySingerId(1)).thenReturn(albums);

            List<AlbumDTO> result = service.getAlbumsOfSinger(1);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAlbumName()).isEqualTo("Album A");
            assertThat(result.get(1).getAlbumName()).isEqualTo("Album B");
        }

        @Test
        @DisplayName("trả về list rỗng khi singer không có album nào")
        void noAlbums_returnsEmpty() {
            when(albumRepository.getBySingerId(99)).thenReturn(List.of());

            assertThat(service.getAlbumsOfSinger(99)).isEmpty();
        }
    }

    @Nested
    @DisplayName("createAlbum()")
    class CreateAlbum {

        @Test
        @DisplayName("tạo album thành công và trả về DTO đúng")
        void success() {
            Singer   s    = singer(1);
            Album    saved = album(10, "New Album", "album/new.jpg", s);
            MultipartFile imgFile = mock(MultipartFile.class);

            when(singerRepository.findById(1)).thenReturn(Optional.of(s));
            when(fileStorageService.storeFile(imgFile, PathStorage.ALBUM)).thenReturn("album/new.jpg");
            when(albumRepository.save(any(Album.class))).thenReturn(saved);

            AlbumDTO result = service.createAlbum("New Album", 1, imgFile);

            assertThat(result.getAlbumName()).isEqualTo("New Album");
            assertThat(result.getAlbumImg()).isEqualTo("album/new.jpg");
            verify(albumRepository).save(any(Album.class));
        }

        @Test
        @DisplayName("ném RuntimeException khi singer không tồn tại")
        void singerNotFound_throws() {
            when(singerRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.createAlbum("Album X", 999, mock(MultipartFile.class)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Singer not found");
        }

        @Test
        @DisplayName("không gọi albumRepository.save khi singer không tồn tại")
        void singerNotFound_noSave() {
            when(singerRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.createAlbum("Album X", 999, mock(MultipartFile.class)))
                    .isInstanceOf(RuntimeException.class);

            verify(albumRepository, never()).save(any());
        }

        @Test
        @DisplayName("lưu đúng singer vào album")
        void savesCorrectSinger() {
            Singer s = singer(5);
            MultipartFile imgFile = mock(MultipartFile.class);

            Album saved = album(20, "Singer5 Album", "img/5.jpg", s);

            when(singerRepository.findById(5)).thenReturn(Optional.of(s));
            when(fileStorageService.storeFile(any(), any())).thenReturn("img/5.jpg");
            when(albumRepository.save(any())).thenReturn(saved);

            service.createAlbum("Singer5 Album", 5, imgFile);

            verify(albumRepository).save(argThat(a -> a.getSinger().getSinger_id() == 5));
        }
    }

    @Nested
    @DisplayName("updateAlbum()")
    class UpdateAlbum {

        @Test
        @DisplayName("cập nhật tên album thành công, giữ nguyên ảnh khi imgFile null")
        void success_noImageChange() {
            Singer s    = singer(1);
            Album entity = album(1, "Old Name", "album/old.jpg", s);

            when(albumRepository.findById(1)).thenReturn(Optional.of(entity));

            service.updateAlbum(1, "New Name", null);

            assertThat(entity.getAlbumName()).isEqualTo("New Name");
            assertThat(entity.getAlbumImg()).isEqualTo("album/old.jpg"); // giữ nguyên
            verify(albumRepository).save(entity);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("cập nhật ảnh album khi imgFile không null")
        void success_withImageChange() {
            Singer s     = singer(1);
            Album entity  = album(2, "Album", "album/old.jpg", s);
            MultipartFile newImg = mock(MultipartFile.class);

            when(albumRepository.findById(2)).thenReturn(Optional.of(entity));
            when(fileStorageService.storeFile(newImg, PathStorage.ALBUM)).thenReturn("album/new.jpg");

            service.updateAlbum(2, "Album", newImg);

            assertThat(entity.getAlbumImg()).isEqualTo("album/new.jpg");
            verify(albumRepository).save(entity);
        }

        @Test
        @DisplayName("ném exception khi album không tồn tại")
        void notFound_throws() {
            when(albumRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAlbum(999, "Name", null))
                    .isInstanceOf(java.util.NoSuchElementException.class);

            verify(albumRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("isSongInAlbum()")
    class IsSongInAlbum {

        @Test
        @DisplayName("trả về true khi bài hát có trong album")
        void songInAlbum_returnsTrue() {
            when(albumRepository.isSongInAlbum(1, 10)).thenReturn(10);

            assertThat(service.isSongInAlbum(1, 10)).isTrue();
        }

        @Test
        @DisplayName("trả về false khi bài hát không có trong album")
        void songNotInAlbum_returnsFalse() {
            when(albumRepository.isSongInAlbum(1, 99)).thenReturn(null);

            assertThat(service.isSongInAlbum(1, 99)).isFalse();
        }
    }

    @Nested
    @DisplayName("addSongToAlbum() / removeSongFromAlbum()")
    class SongAlbumLink {

        @Test
        @DisplayName("addSongToAlbum() – delegate đúng tới repository")
        void addSong_delegatesToRepo() {
            service.addSongToAlbum(1, 10);
            verify(albumRepository).addSongToAlbum(1, 10);
        }

        @Test
        @DisplayName("removeSongFromAlbum() – delegate đúng tới repository")
        void removeSong_delegatesToRepo() {
            service.removeSongFromAlbum(1, 10);
            verify(albumRepository).removeSongFromAlbum(1, 10);
        }
    }

    @Nested
    @DisplayName("deleteAlbumWithSongs()")
    class DeleteAlbum {

        @Test
        @DisplayName("delegate đúng tới repository.delete_album()")
        void delegatesToRepo() {
            service.deleteAlbumWithSongs(5);
            verify(albumRepository).delete_album(5);
        }

        @Test
        @DisplayName("không gọi albumRepository.deleteById")
        void doesNotCallDeleteById() {
            service.deleteAlbumWithSongs(5);
            verify(albumRepository, never()).deleteById(any());
        }
    }
}