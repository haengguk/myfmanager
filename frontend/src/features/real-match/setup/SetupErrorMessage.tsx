export function SetupErrorMessage({ children }: { children: string }) {
  return (
    <div className="rm-setup-error" role="alert">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4 3 20h18ZM12 9v5m0 3v.1" /></svg>
      <span>{children}</span>
    </div>
  );
}
