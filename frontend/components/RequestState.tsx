export function RequestState({ error, retry }: { error?: string; retry?: () => void }) {
  return error
    ? <div className="alert error" role="alert">{error} {retry && <button className="text-button" onClick={retry}>Try again</button>}</div>
    : <p className="loading-state" role="status">Loading data…</p>;
}
