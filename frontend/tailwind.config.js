/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        flux: {
          cream: '#FAF7F2',
          creamDarker: '#F3EFE8',
          dark: '#181715',
          darkCard: '#22201D',
          coral: '#FF5A36',
          coralHover: '#E84B29',
          peach: '#FED7CA',
          peachLight: '#FFE9E2',
          lime: '#E2F899',
          limeLight: '#EEFBC4',
          card: '#FFFFFF',
          textDark: '#191816',
          textMuted: '#6B6864',
          textLight: '#FAF7F2',
          textLightMuted: '#A8A29E',
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
      }
    },
  },
  plugins: [],
}

