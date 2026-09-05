import records from './player-photos.json';

export interface PlayerPhoto {
  playerId: string;
  localPath: string | null;
  originalImageUrl: string | null;
  sourcePageUrl: string | null;
  photographer: string | null;
  rightsHolder: string | null;
  usageTerms: string | null;
  photoYear: number | null;
  teamAtCapture: string | null;
  status: string;
}

const photos: Readonly<Record<string, PlayerPhoto>> = records;

/** Presentation only: the existing PlayerId is the sole lookup key. */
export function playerPhoto(playerId: string): PlayerPhoto | null {
  const photo = Object.prototype.hasOwnProperty.call(photos, playerId) ? photos[playerId] : null;
  return photo?.status === 'acquired' && photo.localPath ? photo : null;
}
