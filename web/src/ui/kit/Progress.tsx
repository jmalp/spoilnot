/** An indeterminate CircularProgressIndicator: 40 px, or 20 px with a 2 px stroke inside buttons. */
export function Progress({ label, small, size }: { label?: string; small?: boolean; size?: number }) {
  const px = size ?? (small ? 20 : 40);
  return (
    <svg
      className={`m3-progress ${small ? 'm3-progress--small' : ''}`}
      width={px}
      height={px}
      viewBox="0 0 48 48"
      role="progressbar"
      aria-label={label}
    >
      <circle cx="24" cy="24" r="18" />
    </svg>
  );
}
