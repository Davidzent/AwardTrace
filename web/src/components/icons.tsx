import type { ReactNode } from 'react';

/**
 * The app's icons (doc 16): 24-unit outlines drawn in the current text color at the current font size. Each one
 * repeats a word beside it, so they're hidden from screen readers.
 */
function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="1em"
      height="1em"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function CheckCircleIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="10" />
      <path d="m8 12 3 3 5-6" />
    </Icon>
  );
}

export function WarningIcon() {
  return (
    <Icon>
      <path d="M12 3 2 20h20L12 3Z" />
      <path d="M12 10v4M12 17h.01" />
    </Icon>
  );
}

export function XCircleIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="10" />
      <path d="m9 9 6 6M15 9l-6 6" />
    </Icon>
  );
}

export function MinusCircleIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="10" />
      <path d="M8 12h8" />
    </Icon>
  );
}

export function QuestionCircleIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="10" />
      <path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.1M12 17h.01" />
    </Icon>
  );
}
