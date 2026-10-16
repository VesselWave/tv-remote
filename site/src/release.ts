export type Release = {
  version: string;
  tag: string;
  file: string;
  size: number;
  sha256: string;
  published: string;
  url: string;
  page: string;
};

export const REPO_URL = 'https://github.com/VesselWave/wave-remote';
export const RELEASES_URL = `${REPO_URL}/releases/latest`;

export function megabytes(bytes: number) {
  return bytes < 1048576 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1048576).toFixed(1)} MB`;
}

export function releaseDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
}
