import { useEffect } from 'react'

/** Sets a unique document title per page (suffix keeps the brand). */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · QueueFlow`
  }, [title])
}
