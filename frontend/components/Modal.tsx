"use client";
import { useEffect, useRef, type ReactNode } from "react";

/** Native dialog provides focus trapping, background inertness and Escape handling. */
export function Modal({ title, children, isBusy, onClose }: {
  title: string; children: ReactNode; isBusy: boolean; onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    const trigger = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (trigger?.isConnected) trigger.focus();
    };
  }, []);
  return <dialog ref={ref} className="modal" aria-labelledby="modal-title"
    onCancel={(event) => { event.preventDefault(); if (!isBusy) onClose(); }}>
    <div className="modal-heading"><h2 id="modal-title">{title}</h2>
      <button type="button" className="icon-button" aria-label="Close dialog" disabled={isBusy} onClick={onClose}>×</button>
    </div>{children}
  </dialog>;
}
