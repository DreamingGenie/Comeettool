<template>
  <main class="intro-page">
    <header class="intro-header">
      <AppLogo variant="blue" />
      <nav>
        <button class="primary" type="button" @click="$router.push('/login')">무료 시작하기</button>
      </nav>
    </header>
    <section class="intro-hero">
      <span>WORK SMARTER, TOGETHER</span>
      <h1>AI와 함께하는<br /><strong>스마트한 팀 협업</strong>, 코밋툴</h1>
      <p>
        공유 문서 편집부터 팀 일정 관리, 지능형 화상 회의까지.<br />
        엔터프라이즈급 AI 보안 기술로 당신의 팀이 더 본질적인 업무에 집중하도록 돕습니다.
      </p>
      <div>
        <button class="primary" type="button" @click="$router.push('/login')">무료로 시작하기</button>
        <button class="demo-btn" type="button" @click="$router.push('/home')">◉ 데모 보기</button>
      </div>
    </section>
    <section class="features">
      <header>
        <span>KEY FEATURES</span>
        <h2>팀 생산성을 극대화하는 <strong>핵심 기능</strong></h2>
        <p>코밋툴만의 지능형 솔루션을 확인해 보세요.</p>
      </header>
      <div class="feature-grid">
        <article class="feature-card document-ai">
          <h3>공유 문서 편집</h3>
          <p>팀원들과 문서를 실시간으로 함께 작성하고, 권한에 따라 안전하게 공유하고 편집합니다.</p>
          <img src="/assets/landing-document-ai.png" alt="코밋툴 공유 문서 화면" />
        </article>
        <article class="feature-card scheduling">
          <h3>팀 일정 관리</h3>
          <p>팀 일정을 캘린더에서 한눈에 확인하고, 회의와 주요 일정을 손쉽게 등록하고 관리합니다.</p>
          <img src="/assets/landing-scheduling.png" alt="코밋툴 팀 일정 화면" />
        </article>
        <article class="feature-card meeting-feature">
          <div>
            <h3>지능형 화상 회의</h3>
            <p>실시간 자동 자막, 회의록 요약과 주요 안건 트래킹까지 회의 전 과정을 AI가 보조합니다.</p>
          </div>
          <img src="/assets/landing-meeting.png" alt="코밋툴 화상회의 화면" />
        </article>
      </div>
    </section>
    <section class="intro-cta">
      <h2>지금 바로 코밋툴과 함께<br />팀의 미래를 설계하세요.</h2>
      <p>모든 핵심 기능을 무료로 체험해 보세요.</p>
      <div>
        <button type="button" @click="$router.push('/login')">무료 체험 시작</button>
        <button type="button" @click="$router.push('/login')">로그인</button>
      </div>
    </section>
  </main>
</template>

<script setup>
import AppLogo from '../../../shared/components/AppLogo.vue'
</script>
