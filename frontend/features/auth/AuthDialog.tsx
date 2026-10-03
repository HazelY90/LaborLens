"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { useAuth } from "@/hooks/useAuth";
import type { AuthView } from "@/contexts/AuthContext";
import { ApiError } from "@/services/apiClient";
import * as auth from "@/services/authService";

const titles = {
  login: "Welcome back",
  register: "Create your account",
  username: "Change username",
  password: "Change password",
  delete: "Delete account"
};

const actions = {
  login: "Log in",
  register: "Create account",
  username: "Save username",
  password: "Update password",
  delete: "Delete account"
};

export function AuthDialog() {
  const { view } = useAuth();

  return view ? <AuthForm key={view} view={view} /> : null;
}

/** Remounting each form clears passwords when switching or closing dialogs. */
function AuthForm({ view }: { view: AuthView }) {
  const { user, close, open, notify } = useAuth();
  const router = useRouter();
  const [isBusy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [field, setField] = useState<string | null>(null);
  const isNewPassword = view === "register" || view === "password";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (isBusy) return;
    const form = new FormData(event.currentTarget);
    const value = (key: string) => String(form.get(key) ?? "");
    const password = value(view === "password" ? "newPassword" : "password");
    setError("");
    setField(null);

    if (isNewPassword && new TextEncoder().encode(password).length > 72) {
      setField(view === "password" ? "newPassword" : "password");
      setError("Password must be no more than 72 UTF-8 bytes.");

      return;
    }

    if (isNewPassword && password !== value("confirmPassword")) {
      setField("confirmPassword");
      setError("Passwords do not match.");

      return;
    }
    setBusy(true);
    try {
      if (view === "register") {
        await auth.register({ email: value("email").trim(), username: value("username").trim(), password });
        open("login");
        notify("Account created. Log in to continue.");

        return;
      }

      if (view === "login") {
        await auth.login({ email: value("email").trim(), password });
        close();
        notify("");
        router.push("/annual-employment-rate");

        return;
      }

      if (view === "username") {
        await auth.rename(value("username").trim());
        close();
        notify("Username updated.");

        return;
      }

      if (view === "password") {
        await auth.changePassword({ currentPassword: value("currentPassword"), newPassword: password });
        router.replace("/");
        open("login");
        notify("Password updated. Please log in again.");

        return;
      }
      await auth.deleteAccount(value("currentPassword"));
      close();
      router.replace("/");
      notify("Your account has been deleted.");
    } catch (error) {
      setError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");

      if (error instanceof ApiError) setField(error.field);
    } finally {
      setBusy(false);
    }
  }

  function input(
    name: string,
    label: string,
    type = "text",
    autoComplete?: string,
    initial?: string
  ) {
    return <label className="field" htmlFor={name}>
      {label}
      <input
        id={name}
        name={name}
        type={type}
        autoComplete={autoComplete}
        defaultValue={initial}
        required
        minLength={(name === "newPassword" || (name === "password" && view === "register")) ? 8 : undefined}
        maxLength={name === "username" ? 50 : name === "email" ? 254 : undefined}
        pattern={name === "username" ? ".*\\S.*" : undefined}
        aria-invalid={field === name}
        aria-describedby={field === name ? "form-error" : undefined}
      />
    </label>;
  }

  return <Modal title={titles[view]} isBusy={isBusy} onClose={close}>
    <p className="modal-description">{view === "login"
      ? "Log in to explore Ireland’s labour market."
      :
      view === "register"
        ? "One account for employment data and policy insights."
        :
        view === "username"
          ? "Choose the name displayed on your account."
          :
          view === "password"
            ? "Changing your password signs you out on all devices."
            :
            "This permanently deletes your account and signs you out on all devices. This action cannot be undone."}</p>
    <FormNotice />
    <form onSubmit={submit}>
      <fieldset disabled={isBusy}>
        {(view === "login" || view === "register") && input("email", "Email address", "email", "username")}
        {(view === "register" || view === "username") && input("username", "Username", "text", "nickname", user?.username)}
        {(view === "password" || view === "delete")
          && input("currentPassword", "Current password", "password", "current-password")}
        {(view === "login" || view === "register")
          && input("password", "Password", "password", view === "login" ? "current-password" : "new-password")}
        {view === "password" && input("newPassword", "New password", "password", "new-password")}
        {isNewPassword
          && <>
            <p className="field-hint">Use at least 8 characters (up to 72 UTF-8 bytes).</p>
            {input("confirmPassword", "Confirm password", "password", "new-password")}
          </>}
        {error && <p id="form-error" className="alert error" role="alert">{error}</p>}
        <button
          className={`button full ${view === "delete" ? "danger" : "primary"}`}
          type="submit"
        >
          {isBusy ? "Please wait…" : actions[view]}</button>
      </fieldset>
    </form>
    {(view === "login" || view === "register")
      && <p className="form-switch">
        {view === "login" ? "New to LaborLens? " : "Already have an account? "}
        <button
          className="text-button"
          disabled={isBusy}
          onClick={() => open(view === "login" ? "register" : "login")}
        >
          {view === "login" ? "Sign up" : "Log in"}</button>
      </p>}
  </Modal>;
}

function FormNotice() {
  const { notice } = useAuth();

  return notice ? <p className="alert" role="status">{notice}</p> : null;
}
