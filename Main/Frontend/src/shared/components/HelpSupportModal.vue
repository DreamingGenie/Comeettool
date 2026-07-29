<template>
  <BaseModal modal-class="help-support-modal" @close="$emit('close')">
    <small>HELP &amp; SUPPORT</small>
    <h2>도움말 및 지원</h2>
    <p class="help-support-modal__intro">
      코밋툴 이용 중 궁금한 점이 있거나 문제가 발생했다면 아래 내용을 확인해 주세요.
    </p>

    <div class="help-support-modal__items">
      <article>
        <span aria-hidden="true">?</span>
        <div>
          <b>서비스 이용 안내</b>
          <p>팀 스페이스를 선택하면 일정, 문서, 회의록과 멤버 정보를 확인할 수 있습니다.</p>
        </div>
      </article>
      <article>
        <span aria-hidden="true">✉</span>
        <div>
          <b>문의하기</b>
          <p>추가 도움이 필요하면 support@committool.com으로 문의해 주세요.</p>
        </div>
      </article>
    </div>

    <button class="primary help-support-modal__confirm" type="button" @click="$emit('close')">
      확인
    </button>
  </BaseModal>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import BaseModal from './BaseModal.vue'

const emit = defineEmits(['close'])

function closeWithEscape(event) {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', closeWithEscape))
onBeforeUnmount(() => window.removeEventListener('keydown', closeWithEscape))
</script>

<style>
.help-support-modal {
  width: min(440px, calc(100vw - 32px));
  padding: 34px 32px 30px;
}

.help-support-modal > small {
  color: var(--blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.4px;
}

.help-support-modal h2 {
  margin: 6px 0 10px;
}

.help-support-modal__intro {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
}

.help-support-modal__items {
  display: grid;
  gap: 10px;
  margin: 24px 0;
}

.help-support-modal__items article {
  display: flex;
  gap: 13px;
  align-items: flex-start;
  padding: 15px;
  border: 1px solid #e0e4ed;
  border-radius: 12px;
  background: #f8f9fc;
}

.help-support-modal__items article > span {
  display: grid;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  background: #e9edff;
  color: var(--blue);
  font-weight: 800;
}

.help-support-modal__items b {
  font-size: 13px;
}

.help-support-modal__items p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.help-support-modal__confirm {
  display: block;
  min-width: 96px;
  margin-left: auto;
}
</style>
