import { startWakeButton } from './wake';

// The build writes the wake function's URL in from WAKE_FUNCTION_URL. Without one, the page keeps its static hours.
const root = document.querySelector<HTMLElement>('[data-wake]');
if (root?.dataset.wakeUrl) {
  startWakeButton(root, root.dataset.wakeUrl);
}
