export const WAKE_PHRASE = 'hey toto';

const WAKE_PATTERN =
  /\b(hey|hay|hi|ok|okay|a)[\s,]*(toto|totto|photo|tota|tote|toe\s?toe|tofu|todo)\b/i;

export function matchWakeWord(text: string): { hit: boolean; rest: string } {
  const m = WAKE_PATTERN.exec(text);
  if (!m) return { hit: false, rest: '' };
  return { hit: true, rest: text.slice(m.index + m[0].length).trim() };
}
