/** What the wake function says about the host (ADR 0020). */
type Status = 'stopped' | 'starting' | 'ready' | 'stopping';

type State = Status | 'spent' | 'unreachable';

const MESSAGES: Record<State, string> = {
  ready: 'The live app is up.',
  starting: "The live app is starting. It's usually ready within 5 minutes, and this page checks every 10 seconds.",
  stopped: 'The live app is asleep. Starting it takes about 5 minutes.',
  stopping: 'The live app is shutting down. Try starting it again in a minute.',
  spent: 'The live app has been started 6 times today, the most a day allows. Try again tomorrow.',
  unreachable: "The wake service didn't answer. Try again in a moment.",
};

/**
 * Shows the live app's state in `root`, from the wake function at `functionUrl`, and lets a visitor start a sleeping
 * host. `root` holds a [data-message] element and a button, and stays hidden until the function first answers, so a
 * page that can't reach it keeps its static hours. While the host starts or stops, it checks again every `pollMs`.
 */
export function startWakeButton(root: HTMLElement, functionUrl: string, pollMs = 10_000): void {
  const message = root.querySelector<HTMLElement>('[data-message]');
  const button = root.querySelector<HTMLButtonElement>('button');
  if (!message || !button) {
    return;
  }
  let timer: ReturnType<typeof setTimeout> | undefined;

  const show = (state: State) => {
    root.dataset.state = state;
    message.textContent = MESSAGES[state];
    button.hidden = state !== 'stopped' && state !== 'unreachable';
    root.hidden = false;
  };

  const follow = (status: Status) => {
    show(status);
    clearTimeout(timer);
    if (status === 'starting' || status === 'stopping') {
      timer = setTimeout(check, pollMs);
    }
  };

  async function check() {
    try {
      const response = await fetch(new URL('status', functionUrl));
      follow(((await response.json()) as { status: Status }).status);
    } catch {
      // Once the state is showing, a failed check is retried; before that, the static hours stand alone.
      if (root.dataset.state) {
        timer = setTimeout(check, pollMs);
      }
    }
  }

  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      const response = await fetch(new URL('wake', functionUrl), { method: 'POST' });
      if (response.status === 429) {
        show('spent');
      } else {
        follow(((await response.json()) as { status: Status }).status);
      }
    } catch {
      show('unreachable');
    } finally {
      button.disabled = false;
    }
  });

  void check();
}
