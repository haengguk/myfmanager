import { useState } from 'react';
import { playerPhoto } from '../playerPhotos';

interface PlayerPortraitProps {
  playerId: string;
  nickname: string;
  size?: 'profile' | 'thumbnail';
}

export function PlayerPortrait({ playerId, nickname, size = 'profile' }: PlayerPortraitProps) {
  const photo = playerPhoto(playerId);
  // Remount only the image state when identity/source changes, including A → B → A.
  return <PortraitImage key={`${playerId}:${photo?.localPath ?? ''}`} src={photo?.localPath ?? null} nickname={nickname} size={size} />;
}

function PortraitImage({ src, nickname, size }: { src: string | null; nickname: string; size: 'profile' | 'thumbnail' }) {
  const [failed, setFailed] = useState(false);
  const available = src !== null && !failed;
  return (
    <span className={`tp-player-portrait tp-player-portrait--${size}`}>
      {available ? (
        <img src={src} alt={`${nickname} 선수 사진`} loading={size === 'thumbnail' ? 'lazy' : 'eager'} decoding="async" onError={() => setFailed(true)} />
      ) : (
        <svg viewBox="0 0 120 150" role="img" aria-label={`${nickname} 선수 사진 ${failed ? '로딩 실패' : '미확보'}, 기본 실루엣`}>
          <circle cx="60" cy="45" r="25" fill="currentColor" />
          <path d="M12 143v-23c0-26 19-43 48-43s48 17 48 43v23Z" fill="currentColor" />
        </svg>
      )}
    </span>
  );
}

export function PlayerPhotoCredit({ playerId }: { playerId: string }) {
  const photo = playerPhoto(playerId);
  if (!photo) return <p className="tp-photo-credit">선수 사진 미확보</p>;
  return (
    <p className="tp-photo-credit">
      사진 · <a href={photo.sourcePageUrl!} target="_blank" rel="noreferrer">{photo.photographer ?? photo.rightsHolder ?? '출처'}</a>
      {photo.photoYear ? ` · ${photo.photoYear}` : ''}{photo.teamAtCapture ? ` · ${photo.teamAtCapture}` : ''}
      <br />{photo.usageTerms ?? '사용 조건 미확인'}
    </p>
  );
}
