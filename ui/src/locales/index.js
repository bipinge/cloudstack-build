import { createI18n } from 'vue-i18n'
import { vueProps } from '@/vue-app'

const loadedLanguage = []
const messages = {}

export const i18n = createI18n({
  locale: 'en',
  fallbackLocale: 'en',
  silentTranslationWarn: true,
  messages: messages,
  silentFallbackWarn: true
})

export function loadLanguageAsync (lang) {
  if (!lang) {
    const locale = vueProps.$localStorage.get('LOCALE')
    lang = (!locale || typeof locale === 'object') ? 'en' : locale
  }
  if (loadedLanguage.includes(lang)) {
    return Promise.resolve(setLanguage(lang))
  }

  return fetch(`locales/${lang}.json`)
    .then(response => response.json())
    .then(json => Promise.resolve(setLanguage(lang, json)))
}

function setLanguage (lang, message) {
  if (i18n) {
    i18n.global.locale = lang

    if (message && Object.keys(message).length > 0) {
      i18n.global.setLocaleMessage(lang, message)
    }
  }

  if (!loadedLanguage.includes(lang)) {
    loadedLanguage.push(lang)
  }

  if (message && Object.keys(message).length > 0) {
    messages[lang] = message
  }
}
