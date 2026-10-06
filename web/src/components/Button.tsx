import type { ComponentProps } from 'react';
import styles from './Button.module.css';

type Props = ComponentProps<'button'> & {
  /** primary for the one main action in a place, secondary for the rest, quiet for small actions inside content */
  variant?: 'primary' | 'secondary' | 'quiet';
  /** Disables the button and says it's working, while what it started is still running. */
  busy?: boolean;
};

/** Every button in the app (doc 16). It defaults to type="button", so it never submits a form by accident. */
export function Button({ variant = 'secondary', busy = false, type = 'button', className, disabled, ...rest }: Props) {
  return (
    <button
      {...rest}
      type={type}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={[styles.button, styles[variant], className].filter(Boolean).join(' ')}
    />
  );
}
