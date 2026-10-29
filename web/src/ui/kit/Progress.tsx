/** An indeterminate CircularProgressIndicator. */
export function Progress({ label }: { label?: string }) {
  return (
    <svg className="m3-progress" viewBox="0 0 48 48" role="progressbar" aria-label={label}>
      <circle cx="24" cy="24" r="18" />
    </svg>
  );
}
