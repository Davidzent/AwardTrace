import { cleanup, render } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { Highlight } from './Highlight';

afterEach(cleanup);

it('renders marks and escaped text, and nothing else', () => {
  const { container } = render(
    <Highlight html={'<mark>Helicopter</mark> &amp; crew &lt;b&gt;services&lt;/b&gt;<img src=x onerror="alert(1)">'} />,
  );

  expect(container.innerHTML).toBe('<mark>Helicopter</mark> &amp; crew &lt;b&gt;services&lt;/b&gt;');
});
