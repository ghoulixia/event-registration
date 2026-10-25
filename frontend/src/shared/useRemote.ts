import { useEffect, useState } from "react";
import { errorMessage } from "../api/client";

type Loader<T> = (signal: AbortSignal) => Promise<T>;
type RemoteState<T> = { load: Loader<T>; version: number; data: T | null; error: string };

export function useRemote<T>(load: Loader<T>) {
  const [state, setState] = useState<RemoteState<T> | null>(null);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal)
      .then(data => { if (!controller.signal.aborted) setState({ load, version, data, error: "" }); })
      .catch(exception => { if (!controller.signal.aborted) setState({ load, version, data: null, error: errorMessage(exception) }); });
    return () => controller.abort();
  }, [load, version]);
  const current = state?.load === load && state.version === version ? state : null;
  return { data: current?.data ?? null, loading: current === null, error: current?.error ?? "", reload: () => setVersion(value => value + 1) };
}
