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
        문서 분석부터 스마트 스케줄링, 지능형 화상 회의까지.<br />
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
          <h3>Document AI</h3>
          <p>수천 장의 문서를 빠르게 분석하고, 핵심 요약부터 콘텐츠 기반 질문 답변까지 제공합니다.</p>
        </article>
        <article class="feature-card scheduling">
          <h3>Smart Scheduling</h3>
          <p>팀원들의 스케줄을 분석해 최적의 미팅 시간을 제안하고 우선순위를 관리합니다.</p>
        </article>
        <article class="feature-card meeting-feature">
          <div>
            <h3>지능형 화상 회의</h3>
            <p>실시간 자동 자막, 회의록 요약과 주요 안건 트래킹까지 회의 전 과정을 AI가 보조합니다.</p>
          </div>
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
