<template>
  <main class="login-page">
    <div class="login-background" :style="{ backgroundImage: `url(${backgroundImage})` }" aria-hidden="true"></div>
    <div class="login-art" aria-hidden="true">
      <img class="login-art-layer login-art-cobloom" :src="cobloomImage" alt="" draggable="false" />
      <img class="login-art-layer login-art-button" :src="loginIconImage" alt="" draggable="false" />
    </div>
    <div class="login-cover-map" aria-label="CoBloom login entrance">
      <button ref="loginHotspotRef" class="login-hotspot" type="button" aria-label="Open login form" @click="openDialog">
        <span>Open login form</span>
      </button>
    </div>

    <Transition name="login-modal">
      <div v-if="dialogOpen" class="login-overlay" role="presentation">
        <section
          class="login-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="login-title"
        >
          <img class="login-board" :src="loginBoardImage" alt="" draggable="false" />
          <button class="login-close" type="button" aria-label="Close login form" @click="closeDialog">x</button>
          <h1 id="login-title" class="sr-only">Login</h1>
          <form class="login-form" @submit.prevent="submit">
            <label>
              <span>Username</span>
              <input
                ref="usernameInputRef"
                v-model.trim="form.username"
                name="username"
                autocomplete="username"
                required
              />
            </label>
            <label>
              <span>Password</span>
              <input
                v-model="form.password"
                name="password"
                type="password"
                autocomplete="current-password"
                required
              />
            </label>
            <p v-if="error" class="login-error" role="alert">{{ error }}</p>
            <button class="login-submit" type="submit" :disabled="loading">
              {{ loading ? 'Logging in...' : 'Login' }}
            </button>
          </form>
        </section>
      </div>
    </Transition>
  </main>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import backgroundImage from '../assets/images/BG.jpg'
import cobloomImage from '../assets/images/Cobloom_icon.png'
import loginIconImage from '../assets/images/Login_icon.png'
import loginBoardImage from '../assets/images/loginBoard.png'

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const loginHotspotRef = ref(null)
const usernameInputRef = ref(null)
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(() => window.addEventListener('keydown', handleWindowKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleWindowKeydown))

async function openDialog() {
  dialogOpen.value = true
  error.value = ''
  await nextTick()
  usernameInputRef.value?.focus()
}

async function closeDialog() {
  if (loading.value) return
  dialogOpen.value = false
  error.value = ''
  await nextTick()
  loginHotspotRef.value?.focus()
}

function handleWindowKeydown(event) {
  if (event.key === 'Escape' && dialogOpen.value) closeDialog()
}

async function submit() {
  if (loading.value) return
  error.value = ''
  if (!form.username || !form.password) {
    error.value = 'Please enter username and password.'
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username, password: form.password })
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (e) {
    error.value = e.message || 'Login failed.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  --login-aspect: calc(2492 / 1402);

  position: fixed;
  inset: 0;
  z-index: 10;
  width: 100vw;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 14%, rgb(255 248 234 / 78%) 0 16%, transparent 32%),
    repeating-radial-gradient(circle at 0 0, rgb(31 27 24 / 4%) 0 1px, transparent 1px 6px),
    #f4efe6;
}

.login-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100vh;
  background-position: center;
  background-repeat: no-repeat;
  background-size: 100% 100%;
  pointer-events: none;
}

.login-art {
  position: absolute;
  top: 50%;
  left: 50%;
  width: max(100vw, calc(100vh * var(--login-aspect)));
  height: max(100vh, calc(100vw / var(--login-aspect)));
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.login-art-layer {
  position: absolute;
  display: block;
  user-select: none;
  pointer-events: none;
}

.login-art-cobloom {
  z-index: 1;
  left: 50%;
  top: 45.7%;
  width: 50.8%;
  height: auto;
  transform: translate(-50%, -50%);
  animation: cobloom-settle 480ms cubic-bezier(0.22, 1, 0.36, 1) both;
  filter: drop-shadow(0 8px 10px rgb(40 35 25 / 18%));
}

.login-art-button {
  z-index: 2;
  left: 49%;
  top: 68.7%;
  width: 21.7%;
  height: auto;
  transform: translate(-50%, -50%);
  animation: login-ticket-ready 520ms 120ms cubic-bezier(0.22, 1, 0.36, 1) both;
  filter: drop-shadow(0 7px 8px rgb(40 35 25 / 18%));
  transition:
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.login-cover-map {
  position: absolute;
  top: 50%;
  left: 50%;
  width: max(100vw, calc(100vh * var(--login-aspect)));
  height: max(100vh, calc(100vw / var(--login-aspect)));
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.login-hotspot {
  position: absolute;
  left: 38.5%;
  top: 65.8%;
  width: 22.8%;
  height: 15.6%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
}

.login-hotspot span {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.login-hotspot:focus-visible {
  outline: 3px solid #009fe3;
  outline-offset: 4px;
}

.login-page:has(.login-hotspot:is(:hover, :focus-visible)) .login-art-button {
  transform: translate(-50%, -52%) rotate(-1deg) scale(1.035);
  filter: drop-shadow(0 10px 12px rgb(40 35 25 / 24%));
}

.login-overlay {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: 28px;
  background:
    radial-gradient(circle at 50% 44%, rgb(255 248 234 / 32%) 0 24%, transparent 46%),
    rgba(31, 27, 24, 0.34);
}

.login-panel {
  position: relative;
  width: min(44vw, 620px);
  min-width: 420px;
  aspect-ratio: 1041 / 783;
  color: #14110f;
  transform: translateY(0);
  filter: drop-shadow(0 20px 30px rgb(92 67 42 / 24%));
}

.login-panel::before {
  content: "";
  position: absolute;
  top: 10%;
  left: 18%;
  z-index: 2;
  width: 86px;
  height: 22px;
  border: 1px solid rgb(31 27 24 / 7%);
  background:
    repeating-linear-gradient(90deg, rgb(255 255 255 / 17%) 0 7px, transparent 7px 14px),
    rgb(236 119 154 / 50%);
  box-shadow: 0 5px 12px rgb(92 67 42 / 12%);
  opacity: 0.82;
  pointer-events: none;
  transform: rotate(-7deg);
}

.login-panel:focus {
  outline: none;
}

.login-board {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
  pointer-events: none;
}

.login-close {
  position: absolute;
  top: 15.5%;
  right: 9.5%;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgb(31 27 24 / 28%);
  background:
    linear-gradient(105deg, rgb(255 255 255 / 34%) 0 24%, transparent 25%),
    rgb(248 214 207 / 82%);
  color: #14110f;
  box-shadow: 0 7px 13px rgb(92 67 42 / 16%);
  cursor: pointer;
  font: 900 18px/1 var(--font-body, system-ui, sans-serif);
  transform: rotate(4deg);
  transition:
    transform 140ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 140ms cubic-bezier(0.16, 1, 0.3, 1);
}

.login-close:hover {
  box-shadow: 0 10px 18px rgb(92 67 42 / 20%);
  transform: translateY(-2px) rotate(0deg);
}

.login-close:focus-visible {
  outline: 3px solid #2f5f8f;
  outline-offset: 3px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.login-form {
  position: absolute;
  top: 34%;
  left: 22%;
  display: grid;
  width: 50%;
  gap: clamp(8px, 1vw, 14px);
}

.login-form label {
  display: grid;
  gap: 7px;
  color: #14110f;
  font: 900 clamp(11px, 0.9vw, 14px) / 1.2 var(--font-body, system-ui, sans-serif);
  transform: rotate(-0.5deg);
}

.login-form input {
  width: 100%;
  height: clamp(34px, 3.2vw, 46px);
  border: 1px solid rgb(31 27 24 / 26%);
  border-radius: 4px 2px 5px 3px;
  background:
    linear-gradient(105deg, rgb(255 255 255 / 42%) 0 18%, transparent 19%),
    rgb(255 253 247 / 72%);
  color: #14110f;
  box-shadow: 0 6px 12px rgb(92 67 42 / 13%);
  font: 700 clamp(13px, 1vw, 16px) / 1.2 var(--font-body, system-ui, sans-serif);
  outline: none;
  padding: 0 12px;
}

.login-form input:focus-visible {
  background:
    linear-gradient(105deg, rgb(255 255 255 / 48%) 0 18%, transparent 19%),
    rgb(255 253 247 / 88%);
  box-shadow: 0 0 0 3px rgb(47 95 143 / 32%), 0 0 0 5px rgb(255 253 247 / 86%), 0 6px 12px rgb(92 67 42 / 13%);
}

.login-error {
  margin: 0;
  border: 1px solid rgb(31 27 24 / 24%);
  border-radius: 4px 2px 5px 3px;
  background:
    linear-gradient(105deg, rgb(255 255 255 / 26%) 0 20%, transparent 21%),
    rgb(248 214 207 / 88%);
  color: #14110f;
  box-shadow: 0 6px 12px rgb(92 67 42 / 13%);
  padding: 8px 10px;
  font: 900 clamp(11px, 0.9vw, 13px) / 1.35 var(--font-body, system-ui, sans-serif);
}

.login-submit {
  width: 48%;
  min-height: clamp(34px, 3.2vw, 46px);
  justify-self: center;
  border: 1px solid rgb(31 27 24 / 24%);
  border-radius: 3px 7px 4px 6px;
  background:
    linear-gradient(105deg, rgb(255 255 255 / 32%) 0 22%, transparent 23%),
    #d94a38;
  color: #fffdf7;
  box-shadow: 0 8px 15px rgb(92 67 42 / 18%);
  cursor: pointer;
  font: 900 clamp(13px, 1vw, 16px) / 1 var(--font-body, system-ui, sans-serif);
  transform: rotate(1deg);
  transition:
    transform 140ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 140ms cubic-bezier(0.16, 1, 0.3, 1),
    opacity 140ms cubic-bezier(0.16, 1, 0.3, 1);
}

.login-submit:hover:not(:disabled) {
  background:
    linear-gradient(105deg, rgb(255 255 255 / 32%) 0 22%, transparent 23%),
    #2f5f8f;
  transform: rotate(0deg) translateY(-2px);
  box-shadow: 0 12px 20px rgb(92 67 42 / 22%);
}

.login-submit:active:not(:disabled) {
  transform: rotate(0deg) translateY(1px);
  box-shadow: 0 5px 11px rgb(92 67 42 / 18%);
}

.login-submit:focus-visible {
  outline: 3px solid #2f5f8f;
  outline-offset: 3px;
}

.login-submit:disabled {
  cursor: wait;
  opacity: 0.68;
}

@keyframes cobloom-settle {
  from {
    opacity: 0;
    transform: translate(-50%, -55%) rotate(-1.2deg) scale(0.985);
  }

  to {
    opacity: 1;
    transform: translate(-50%, -50%) rotate(0deg) scale(1);
  }
}

@keyframes login-ticket-ready {
  0% {
    opacity: 0;
    transform: translate(-50%, -42%) rotate(1.4deg) scale(0.96);
  }

  100% {
    opacity: 1;
    transform: translate(-50%, -50%) rotate(0deg) scale(1);
  }
}

.login-modal-enter-active,
.login-modal-leave-active {
  transition: opacity 160ms ease-out;
}

.login-modal-enter-active .login-panel,
.login-modal-leave-active .login-panel {
  transition: transform 180ms ease-out;
}

.login-modal-enter-from,
.login-modal-leave-to {
  opacity: 0;
}

.login-modal-enter-from .login-panel,
.login-modal-leave-to .login-panel {
  transform: translateY(14px);
}

@media (max-width: 900px) {
  .login-panel {
    width: min(88vw, 560px);
    min-width: 0;
  }

  .login-form {
    top: 34.5%;
    left: 20%;
    width: 54%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-art-cobloom,
  .login-art-button {
    animation: none;
    transition: none;
  }

  .login-submit,
  .login-modal-enter-active,
  .login-modal-leave-active,
  .login-modal-enter-active .login-panel,
  .login-modal-leave-active .login-panel {
    transition: none;
  }
}
</style>
