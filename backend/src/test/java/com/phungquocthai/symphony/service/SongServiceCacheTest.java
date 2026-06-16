package com.phungquocthai.symphony.service;

import com.phungquocthai.symphony.constant.ErrorCode;
import com.phungquocthai.symphony.constant.PathStorage;
import com.phungquocthai.symphony.dto.*;
import com.phungquocthai.symphony.entity.Song;
import com.phungquocthai.symphony.exception.AppException;
import com.phungquocthai.symphony.mapper.CategoryMapper;
import com.phungquocthai.symphony.mapper.SongCreateMapper;
import com.phungquocthai.symphony.mapper.SongMapper;
import com.phungquocthai.symphony.mapper.TopSongMapper;
import com.phungquocthai.symphony.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongServiceCacheTest {

    @Mock private SongRepository     songRepository;
    @Mock private SingerRepository   singerRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private ListenRepository   listenRepository;
    @Mock private PlaylistRepository playlistRepository;
    @Mock private AlbumRepository    albumRepository;

    @Mock private SongMapper       songMapper;
    @Mock private SongCreateMapper songCreateMapper;
    @Mock private TopSongMapper    topSongMapper;
    @Mock private CategoryMapper   categoryMapper;

    @Mock private FileStorageService  fileStorageService;
    @Mock private ExcelExportUtil     excelExportUtil;
    @Mock private NotificationService notificationService;
    @Mock private AISearchService     aiSearchService;
    @Mock private CacheManager        cacheManager;
    @Mock private Cache               cache;

    @InjectMocks
    private SongServiceCache service;

    private void loginAs(Integer userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId == null ? "anonymousUser" : userId.toString(),
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private SongDTO sampleSongDTO(int id) {
        return SongDTO.builder()
                .song_id(id)
                .songName("Song " + id)
                .song_img("img_" + id + ".jpg")
                .path("path_" + id + ".mp3")
                .total_listens(100)
                .duration(200)
                .releaseDate(LocalDate.of(2024, 1, 1))
                .author("Author")
                .isVip(false)
                .categories(List.of())
                .singers(List.of())
                .active(true)
                .build();
    }

    private Song songEntity(int id, String path) {
        Song s = new Song();
        s.setSong_id(id);
        s.setPath(path);
        s.setSongName("Song " + id);
        s.setSingers(new LinkedHashSet<>());
        return s;
    }

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        loginAs(null);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        private SongCreateDTO makeDTO(boolean isVip, List<Integer> singerIds, List<Integer> catIds) {
            SongCreateDTO dto = new SongCreateDTO();
            dto.setIsVip(isVip);
            dto.setSingersId(singerIds);
            dto.setCategoryIds(catIds);
            return dto;
        }

        @Test
        @DisplayName("lưu bài hát thường và clear 4 cache")
        void success_normalSong() {
            SongCreateDTO dto  = makeDTO(false, List.of(1), List.of(10));
            Song          saved = songEntity(99, "music/new.mp3");
            SongDTO       expected = sampleSongDTO(99);

            when(songCreateMapper.toEntity(dto)).thenReturn(saved);
            when(fileStorageService.storeFile(any(), eq(PathStorage.MUSIC_NORMAL))).thenReturn("music/new.mp3");
            when(fileStorageService.storeFile(any(), eq(PathStorage.LYRIC))).thenReturn("lyric/new.txt");
            when(fileStorageService.storeFile(any(), eq(PathStorage.LRC))).thenReturn("lrc/new.lrc");
            when(fileStorageService.storeFile(any(), eq(PathStorage.MUSIC_IMG))).thenReturn("img/new.jpg");
            when(songRepository.save(saved)).thenReturn(saved);
            when(songMapper.toDTO(saved)).thenReturn(expected);
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            SongDTO result = service.create(dto,
                    mock(MultipartFile.class), mock(MultipartFile.class),
                    mock(MultipartFile.class), mock(MultipartFile.class));

            assertThat(result.getSong_id()).isEqualTo(99);
            verify(cache, times(4)).clear();
        }

        @Test
        @DisplayName("bài VIP dùng PathStorage.MUSIC_VIP thay vì MUSIC_NORMAL")
        void vipSong_usesVipStorage() {
            SongCreateDTO dto  = makeDTO(true, List.of(1), List.of(10));
            Song          saved = songEntity(100, "vip/new.mp3");

            when(songCreateMapper.toEntity(dto)).thenReturn(saved);
            when(fileStorageService.storeFile(any(), eq(PathStorage.MUSIC_VIP))).thenReturn("vip/new.mp3");
            when(fileStorageService.storeFile(any(), eq(PathStorage.LYRIC))).thenReturn("lyric/new.txt");
            when(fileStorageService.storeFile(any(), eq(PathStorage.LRC))).thenReturn("lrc/new.lrc");
            when(fileStorageService.storeFile(any(), eq(PathStorage.MUSIC_IMG))).thenReturn("img/new.jpg");
            when(songRepository.save(saved)).thenReturn(saved);
            when(songMapper.toDTO(saved)).thenReturn(sampleSongDTO(100));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.create(dto,
                    mock(MultipartFile.class), mock(MultipartFile.class),
                    mock(MultipartFile.class), mock(MultipartFile.class));

            verify(fileStorageService).storeFile(any(), eq(PathStorage.MUSIC_VIP));
            verify(fileStorageService, never()).storeFile(any(), eq(PathStorage.MUSIC_NORMAL));
        }

        @Test
        @DisplayName("gọi notification và AI sau khi lưu")
        void callsNotificationAndAI() {
            SongCreateDTO dto  = makeDTO(false, List.of(1), List.of(10));
            Song          saved = songEntity(101, "music/song101.mp3");

            when(songCreateMapper.toEntity(dto)).thenReturn(saved);
            when(fileStorageService.storeFile(any(), any())).thenReturn("any/path");
            when(songRepository.save(saved)).thenReturn(saved);
            when(songMapper.toDTO(saved)).thenReturn(sampleSongDTO(101));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.create(dto,
                    mock(MultipartFile.class), mock(MultipartFile.class),
                    mock(MultipartFile.class), mock(MultipartFile.class));

            verify(notificationService).sendNotificationToAllUsers(eq(1), anyString(), anyString());
            verify(aiSearchService).updateAiData(saved.getPath());
        }

        @Test
        @DisplayName("gọi addSongToSinger và addSongToCategory đúng số lần")
        void linksToSingersAndCategories() {
            SongCreateDTO dto  = makeDTO(false, List.of(1, 2), List.of(10, 11, 12));
            Song          saved = songEntity(102, "music/102.mp3");

            when(songCreateMapper.toEntity(dto)).thenReturn(saved);
            when(fileStorageService.storeFile(any(), any())).thenReturn("any");
            when(songRepository.save(saved)).thenReturn(saved);
            when(songMapper.toDTO(saved)).thenReturn(sampleSongDTO(102));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.create(dto,
                    mock(MultipartFile.class), mock(MultipartFile.class),
                    mock(MultipartFile.class), mock(MultipartFile.class));

            verify(songRepository, times(2)).addSongToSinger(anyInt(), eq(102));
            verify(songRepository, times(3)).addSongToCategory(anyInt(), eq(102));
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("ném AppException khi bài hát không tồn tại")
        void notFound_throws() {
            SongUpdateDTO dto = new SongUpdateDTO();
            dto.setSong_id(999);

            when(songRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto, null, null, null))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SONG_NOT_EXISTED);
        }

        @Test
        @DisplayName("cập nhật thành công và evict songDetail + clear 4 cache list")
        void success_evictsCorrectly() {
            Song existing = songEntity(5, "path/5.mp3");

            SongUpdateDTO dto = new SongUpdateDTO();
            dto.setSong_id(5);
            dto.setSongName("New Name");
            dto.setAuthor("Author");
            dto.setDuration(180);
            dto.setIsVip(false);

            when(songRepository.findById(5)).thenReturn(Optional.of(existing));
            when(songRepository.save(existing)).thenReturn(existing);
            when(songMapper.toDTO(existing)).thenReturn(sampleSongDTO(5));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.update(dto, null, null, null);

            verify(cache).evict(5);           // evict chính xác key
            verify(cache, times(4)).clear();  // clear 4 cache list
        }

        @Test
        @DisplayName("cập nhật lyric/lrc/img khi file không null")
        void updatesFilesWhenProvided() {
            Song existing = songEntity(6, "path/6.mp3");

            SongUpdateDTO dto = new SongUpdateDTO();
            dto.setSong_id(6);
            dto.setSongName("Song 6");
            dto.setAuthor("Author");
            dto.setDuration(200);
            dto.setIsVip(false);

            MultipartFile lyricFile   = mock(MultipartFile.class);
            MultipartFile lrcFile     = mock(MultipartFile.class);
            MultipartFile songImgFile = mock(MultipartFile.class);

            when(songRepository.findById(6)).thenReturn(Optional.of(existing));
            when(fileStorageService.storeFile(lyricFile,   PathStorage.LYRIC)).thenReturn("lyric/6.txt");
            when(fileStorageService.storeFile(lrcFile,     PathStorage.LRC)).thenReturn("lrc/6.lrc");
            when(fileStorageService.storeFile(songImgFile, PathStorage.MUSIC_IMG)).thenReturn("img/6.jpg");
            when(songRepository.save(existing)).thenReturn(existing);
            when(songMapper.toDTO(existing)).thenReturn(sampleSongDTO(6));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.update(dto, lyricFile, lrcFile, songImgFile);

            assertThat(existing.getLyric()).isEqualTo("lyric/6.txt");
            assertThat(existing.getLrc()).isEqualTo("lrc/6.lrc");
            assertThat(existing.getSong_img()).isEqualTo("img/6.jpg");
        }

        @Test
        @DisplayName("không gọi fileStorageService khi tất cả file đều null")
        void noFileUpdates_whenAllNull() {
            Song existing = songEntity(7, "path/7.mp3");

            SongUpdateDTO dto = new SongUpdateDTO();
            dto.setSong_id(7);
            dto.setSongName("Song 7");
            dto.setAuthor("Author");
            dto.setDuration(200);
            dto.setIsVip(false);

            when(songRepository.findById(7)).thenReturn(Optional.of(existing));
            when(songRepository.save(existing)).thenReturn(existing);
            when(songMapper.toDTO(existing)).thenReturn(sampleSongDTO(7));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.update(dto, null, null, null);

            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("không save khi findById ném exception")
        void doesNotSaveOnNotFound() {
            SongUpdateDTO dto = new SongUpdateDTO();
            dto.setSong_id(404);

            when(songRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto, null, null, null))
                    .isInstanceOf(AppException.class);

            verify(songRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("xóa hẳn bài hát khi không còn singer nào giữ")
        void fullCleanup_whenNoSingerLeft() {
            Song song = songEntity(7, "music/song7.mp3");

            when(songRepository.findById(7)).thenReturn(Optional.of(song));
            when(singerRepository.havePresent(7)).thenReturn(null);
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.delete(1, 7);

            verify(songRepository).updateIsActive(7, false);
            verify(playlistRepository).deleteAllBySongId(7);
            verify(favoriteRepository).deleteAllBySongId(7);
            verify(listenRepository).deleteAllBySongId(7);
            verify(albumRepository).deleteAllBySongId(7);
            verify(categoryRepository).deleteAllBySongId(7);
            verify(aiSearchService).removeAiData("music/song7.mp3");
        }

        @Test
        @DisplayName("không xóa bài hát khi vẫn còn singer khác giữ")
        void skipCleanup_whenSingerStillPresent() {
            Song song = songEntity(8, "music/song8.mp3");

            when(songRepository.findById(8)).thenReturn(Optional.of(song));
            when(singerRepository.havePresent(8)).thenReturn(1);

            service.delete(2, 8);

            verify(songRepository, never()).updateIsActive(any(), anyBoolean());
            verify(aiSearchService, never()).removeAiData(any());
            verify(playlistRepository, never()).deleteAllBySongId(any());
        }

        @Test
        @DisplayName("luôn gọi deletePresent và deleteBySongIdAndSingerOwnership bất kể còn singer hay không")
        void alwaysRemovesSingerLink() {
            Song song = songEntity(9, "music/song9.mp3");

            when(songRepository.findById(9)).thenReturn(Optional.of(song));
            when(singerRepository.havePresent(9)).thenReturn(1); // vẫn còn singer khác

            service.delete(3, 9);

            verify(singerRepository).deletePresent(3, 9);
            verify(singerRepository).deleteBySongIdAndSingerOwnership(9, 3);
        }

        @Test
        @DisplayName("evict songDetail và clear tất cả cache khi xóa hoàn toàn")
        void evictsAllCaches_whenFullDelete() {
            Song song = songEntity(10, "music/song10.mp3");

            when(songRepository.findById(10)).thenReturn(Optional.of(song));
            when(singerRepository.havePresent(10)).thenReturn(null);
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.delete(1, 10);

            // evict songDetail key + newSongs "single"
            verify(cache, atLeast(2)).evict(any());
            // clear songs / newSongs / topSongs / songSearch
            verify(cache, atLeast(4)).clear();
        }
    }

    @Nested
    @DisplayName("reverseFavorite()")
    class ReverseFavorite {

        @Test
        @DisplayName("thêm favorite khi chưa tồn tại")
        void insert_whenNotExists() {
            when(favoriteRepository.findFavorited(10, 1)).thenReturn(null);

            service.reverseFavorite(10, 1);

            verify(favoriteRepository).insertFavorite(10, 1);
            verify(favoriteRepository, never()).deleteBySongIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("xóa favorite khi đã tồn tại")
        void delete_whenExists() {
            when(favoriteRepository.findFavorited(10, 1)).thenReturn(1);

            service.reverseFavorite(10, 1);

            verify(favoriteRepository).deleteBySongIdAndUserId(10, 1);
            verify(favoriteRepository, never()).insertFavorite(any(), any());
        }

        @Test
        @DisplayName("mỗi lần gọi chỉ thực hiện đúng 1 thao tác (insert hoặc delete)")
        void exactlyOneOperation() {
            when(favoriteRepository.findFavorited(20, 5)).thenReturn(null);

            service.reverseFavorite(20, 5);

            verify(favoriteRepository, times(1)).insertFavorite(20, 5);
            verify(favoriteRepository, never()).deleteBySongIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("không gọi insertFavorite khi bản ghi tồn tại với giá trị bất kỳ > 0")
        void delete_whenExistsWithAnyPositiveValue() {
            when(favoriteRepository.findFavorited(30, 7)).thenReturn(999);

            service.reverseFavorite(30, 7);

            verify(favoriteRepository).deleteBySongIdAndUserId(30, 7);
            verify(favoriteRepository, never()).insertFavorite(any(), any());
        }
    }

    @Nested
    @DisplayName("updateTotalListenOfSong()")
    class UpdateTotalListen {

        @Test
        @DisplayName("tăng listen count +1 và evict cache đúng key")
        void incrementsAndEvicts() {
            Song song = songEntity(3, "path/3.mp3");
            song.setTotal_listens(50);

            when(songRepository.findById(3)).thenReturn(Optional.of(song));
            when(songRepository.save(song)).thenReturn(song);
            when(cacheManager.getCache("songDetail")).thenReturn(cache);

            Integer result = service.updateTotalListenOfSong(3);

            assertThat(result).isEqualTo(51);
            verify(cache).evict(3);
        }

        @Test
        @DisplayName("listen count bắt đầu từ 0 tăng lên 1")
        void incrementFromZero() {
            Song song = songEntity(4, "path/4.mp3");
            song.setTotal_listens(0);

            when(songRepository.findById(4)).thenReturn(Optional.of(song));
            when(songRepository.save(song)).thenReturn(song);
            when(cacheManager.getCache("songDetail")).thenReturn(cache);

            assertThat(service.updateTotalListenOfSong(4)).isEqualTo(1);
        }

        @Test
        @DisplayName("ném AppException khi bài hát không tồn tại")
        void throwsWhenNotFound() {
            when(songRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTotalListenOfSong(999))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SONG_NOT_EXISTED);
        }

        @Test
        @DisplayName("không evict cache khi bài hát không tồn tại")
        void noEvictOnException() {
            when(songRepository.findById(888)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTotalListenOfSong(888))
                    .isInstanceOf(AppException.class);

            verifyNoInteractions(cacheManager);
        }
    }

    @Nested
    @DisplayName("getSongById()")
    class GetSongById {

        @Test
        @DisplayName("trả về bài hát kèm isFavorite=true cho user đã favorite")
        void favorite_forLoggedInUser() {
            loginAs(42);

            SongDTO cached = sampleSongDTO(1);
            when(songRepository.findById(1)).thenReturn(Optional.of(new Song()));
            when(songMapper.toDTO(any())).thenReturn(cached);
            when(favoriteRepository.findByPrimaryKey(1, 42)).thenReturn(1);

            assertThat(service.getSongById(1).isFavorite()).isTrue();
        }

        @Test
        @DisplayName("isFavorite=false khi user chưa favorite")
        void notFavorited_returnsFalse() {
            loginAs(42);

            SongDTO cached = sampleSongDTO(2);
            when(songRepository.findById(2)).thenReturn(Optional.of(new Song()));
            when(songMapper.toDTO(any())).thenReturn(cached);
            when(favoriteRepository.findByPrimaryKey(2, 42)).thenReturn(0);

            assertThat(service.getSongById(2).isFavorite()).isFalse();
        }

        @Test
        @DisplayName("isFavorite=false khi anonymous user (không đăng nhập)")
        void anonymous_favoriteAlwaysFalse() {
            // loginAs(null) đã được set trong @BeforeEach
            SongDTO cached = sampleSongDTO(3);
            when(songRepository.findById(3)).thenReturn(Optional.of(new Song()));
            when(songMapper.toDTO(any())).thenReturn(cached);

            SongDTO result = service.getSongById(3);

            assertThat(result.isFavorite()).isFalse();
            verifyNoInteractions(favoriteRepository);
        }

        @Test
        @DisplayName("ném AppException khi bài hát không tồn tại")
        void throwsWhenNotFound() {
            when(songRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSongById(999))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SONG_NOT_EXISTED);
        }
    }

    @Nested
    @DisplayName("listeningStatistics()")
    class ListeningStatistics {

        @Test
        @DisplayName("mapping Object[] row → ListeningStatsDTO đúng với java.sql.Date")
        void mapsRowCorrectly_sqlDate() {
            Date sqlDate = Date.valueOf("2024-06-01");
            when(songRepository.getHourlyListeningStats(1))
                    .thenReturn(List.<Object[]>of(new Object[]{1, sqlDate, 14, 200L}));

            List<ListeningStatsDTO> result = service.listeningStatistics(1);

            assertThat(result).hasSize(1);
            ListeningStatsDTO stat = result.get(0);
            assertThat(stat.getSong_id()).isEqualTo(1);
            assertThat(stat.getListen_date()).isEqualTo(LocalDate.of(2024, 6, 1));
            assertThat(stat.getHour()).isEqualTo(14);
            assertThat(stat.getTotal_listens_per_hour()).isEqualTo(200L);
        }

        @Test
        @DisplayName("mapping đúng với java.util.Date (nhánh else if)")
        void mapsRowCorrectly_utilDate() {
            java.util.Date utilDate = new java.util.Date(
                    Date.valueOf("2024-07-15").getTime());

            when(songRepository.getHourlyListeningStats(2))
                    .thenReturn(List.<Object[]>of(new Object[]{2, utilDate, 9, 50L}));

            List<ListeningStatsDTO> result = service.listeningStatistics(2);

            assertThat(result.get(0).getListen_date()).isEqualTo(LocalDate.of(2024, 7, 15));
        }

        @Test
        @DisplayName("trả về list rỗng khi không có dữ liệu")
        void emptyRows_returnsEmptyList() {
            when(songRepository.getHourlyListeningStats(99)).thenReturn(List.of());

            assertThat(service.listeningStatistics(99)).isEmpty();
        }

        @Test
        @DisplayName("nhiều row được map thành nhiều DTO")
        void multipleRows_mappedCorrectly() {
            Date d1 = Date.valueOf("2024-01-01");
            Date d2 = Date.valueOf("2024-01-02");

            when(songRepository.getHourlyListeningStats(5))
                    .thenReturn(List.of(
                            new Object[]{5, d1, 8,  100L},
                            new Object[]{5, d2, 20, 300L}
                    ));

            List<ListeningStatsDTO> result = service.listeningStatistics(5);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getHour()).isEqualTo(8);
            assertThat(result.get(1).getHour()).isEqualTo(20);
            assertThat(result.get(1).getTotal_listens_per_hour()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("disable() / enable()")
    class DisableEnable {

        @Test
        @DisplayName("disable() – set active=false và gọi removeAiData")
        void disable_deactivatesAndRemovesAI() {
            Song song = songEntity(11, "music/11.mp3");

            when(songRepository.findById(11)).thenReturn(Optional.of(song));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.disable(11);

            verify(songRepository).updateIsActive(11, false);
            verify(aiSearchService).removeAiData("music/11.mp3");
        }

        @Test
        @DisplayName("enable() – set active=true và gọi updateAiData")
        void enable_activatesAndUpdatesAI() {
            Song song = songEntity(12, "music/12.mp3");

            when(songRepository.findById(12)).thenReturn(Optional.of(song));
            when(cacheManager.getCache(anyString())).thenReturn(cache);

            service.enable(12);

            verify(songRepository).updateIsActive(12, true);
            verify(aiSearchService).updateAiData("music/12.mp3");
        }
    }
}