// Data "de hoje" em ISO (yyyy-mm-dd) no fuso LOCAL do navegador.
// new Date().toISOString() usa UTC: entre 21h e 23h59 em UTC-3 o filtro
// padrão do histórico consultava o dia seguinte e não listava as senhas do dia.
export function todayLocalISODate(now: Date = new Date()): string {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
