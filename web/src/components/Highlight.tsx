/**
 * Renders a search highlight: HTML-escaped text with {@code <mark>} around matches (doc 07). The markup is parsed and
 * rebuilt rather than injected, so nothing but text and marks can reach the page.
 */
export function Highlight({ html }: { html: string }) {
  const nodes = new DOMParser().parseFromString(html, 'text/html').body.childNodes;
  return (
    <>
      {Array.from(nodes, (node, index) =>
        node.nodeName === 'MARK' ? <mark key={index}>{node.textContent}</mark> : node.textContent,
      )}
    </>
  );
}
