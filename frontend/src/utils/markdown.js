import DOMPurify from 'dompurify'
import { marked } from 'marked'

const SANITIZE_OPTIONS = {
  USE_PROFILES: { html: true },
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'textarea', 'select', 'meta', 'base'],
  FORBID_ATTR: ['style']
}

export function renderSafeMarkdown(source) {
  const rendered = marked.parse(String(source || ''))
  return DOMPurify.sanitize(rendered, SANITIZE_OPTIONS)
}
