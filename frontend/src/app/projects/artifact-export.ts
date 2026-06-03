import type { ProjectDetailResponse } from './project';

/**
 * URL/file-safe slug that keeps Unicode letters and numbers (the project domain
 * includes Cyrillic), collapses whitespace to dashes and trims stray dashes.
 */
export function slugify(value: string): string {
  const slug = value
    .normalize('NFC')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^\p{L}\p{N}-]+/gu, '')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'untitled';
}

/** Returns a function that suffixes repeated names so files never collide. */
function uniqueNamer(): (base: string) => string {
  const seen = new Map<string, number>();
  return base => {
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    return count === 0 ? base : `${base}-${count + 1}`;
  };
}

function triggerDownload(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/** Downloads a single text artifact (Markdown or Mermaid) as a file. */
export function downloadText(filename: string, text: string, mime = 'text/plain'): void {
  triggerDownload(filename, new Blob([text], { type: `${mime};charset=utf-8` }));
}

/**
 * Bundles every artifact into a single, folder-organized `.zip`. JSZip is
 * lazily imported so it never lands in the initial bundle.
 */
export async function exportAllAsZip(project: ProjectDetailResponse): Promise<void> {
  const artifacts = project.artifacts;
  if (!artifacts) return;

  const { default: JSZip } = await import('jszip');
  const zip = new JSZip();

  const diagrams = zip.folder('diagrams')!;
  diagrams.file('c4-context.mmd', artifacts.c4Context ?? '');
  diagrams.file('c4-container.mmd', artifacts.c4Container ?? '');

  const sequence = diagrams.folder('sequence')!;
  const seqName = uniqueNamer();
  for (const diagram of artifacts.sequenceDiagrams ?? []) {
    sequence.file(`${seqName(slugify(diagram.title))}.mmd`, diagram.code ?? '');
  }

  zip.file('sdd.md', artifacts.sdd ?? '');

  const adrFolder = zip.folder('adrs')!;
  const adrName = uniqueNamer();
  for (const adr of artifacts.adrs ?? []) {
    adrFolder.file(`${adrName(slugify(adr.title))}.md`, adr.markdown ?? '');
  }

  const blob = await zip.generateAsync({ type: 'blob' });
  triggerDownload(`${slugify(project.name)}-artifacts.zip`, blob);
}
