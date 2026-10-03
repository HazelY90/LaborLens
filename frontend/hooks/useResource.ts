"use client";

import { useEffect, useState } from "react";

/** Cancel obsolete requests and never display a result for an old selection. */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ load: typeof load; attempt: number; data?: T; error?: string }>();
  useEffect(
    () => {
      const controller = new AbortController();
      load(controller.signal).then((data) => {
        if (!controller.signal.aborted) setState({ load, attempt, data });
      }).catch((error: unknown) => {
        if (!controller.signal.aborted) setState({ load, attempt, error: error instanceof Error ? error.message : "Unable to load data." });
      });

      return () => controller.abort();
    },
    [load, attempt]
  );
  // Hide stale data immediately, before the replacement request completes.
  const current = state?.load === load && state.attempt === attempt ? state : undefined;

  return {
    data: current?.data,
    error: current?.error,
    isLoading: !current,
    retry: () => setAttempt((value) => value + 1)
  };
}
