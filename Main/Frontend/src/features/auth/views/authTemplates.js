export function createAuthTemplates({ state, onboardingMock, renderLogo }) {
  const authView = (signup = false) =>
    `<main class="auth-page"><div class="auth-logo">${renderLogo()}</div><form class="auth-card" data-submit="auth"><h1>${signup ? '회원가입' : '로그인'}</h1><label class="field">이메일 주소<input name="email" type="email" placeholder="example@committool.com" required></label><label class="field"><span class="field-line">비밀번호 ${signup ? '' : '<button type="button">비밀번호를 잊으셨나요?</button>'}</span><div class="password-box"><input name="password" type="password" required><button type="button" data-action="eye">◉</button></div></label>${signup ? '<label class="field">비밀번호 확인<div class="password-box"><input name="passwordConfirm" type="password" required><button type="button" data-action="eye">◉</button></div></label>' : ''}<button class="primary block">${signup ? '회원가입' : '로그인'} →</button><div class="auth-divider"></div><p class="switch">${signup ? '이미 계정이 있으신가요?' : '아직 계정이 없으신가요?'} <button type="button" data-view="${signup ? 'login' : 'signup'}">${signup ? '로그인' : '회원가입'}</button></p></form><div class="auth-links">이용약관　 개인정보처리방침　 고객지원</div></main>`

  const introView = () =>
    `<main class="intro-page"><header class="intro-header">${renderLogo()}<nav><button class="primary" data-view="login">무료 시작하기</button></nav></header><section class="intro-hero"><span>WORK SMARTER, TOGETHER</span><h1>AI와 함께하는<br><strong>스마트한 팀 협업</strong>, 코밋툴</h1><p>문서 분석부터 스마트 스케줄링, 지능형 화상 회의까지.<br>엔터프라이즈급 AI 보안 기술로 당신의 팀이 더 본질적인 업무에 집중하도록 돕습니다.</p><div><button class="primary" data-view="login">무료로 시작하기</button><button class="demo-btn" data-action="show-demo">◉ 데모 보기</button></div></section><section class="features"><header><span>KEY FEATURES</span><h2>팀 생산성을 극대화하는 <strong>핵심 기능</strong></h2><p>코밋툴만의 지능형 솔루션을 확인해 보세요.</p></header><div class="feature-grid"><article class="feature-card document-ai"><h3>Document AI</h3><p>수천 장의 문서를 빠르게 분석하고, 핵심 요약부터 콘텐츠 기반 질문 답변까지 제공합니다.</p></article><article class="feature-card scheduling"><h3>Smart Scheduling</h3><p>팀원들의 스케줄을 분석해 최적의 미팅 시간을 제안하고 우선순위를 관리합니다.</p></article><article class="feature-card meeting-feature"><div><h3>지능형 화상 회의</h3><p>실시간 자동 자막, 회의록 요약과 주요 안건 트래킹까지 회의 전 과정을 AI가 보조합니다.</p></div></article></div></section><section class="intro-cta"><h2>지금 바로 코밋툴과 함께<br>팀의 미래를 설계하세요.</h2><p>모든 핵심 기능을 무료로 체험해 보세요.</p><div><button data-view="login">무료 체험 시작</button><button data-view="login">로그인</button></div></section></main>`

  const onboardingView = () => {
    const step = onboardingMock.steps[state.step]
    const progress = ((state.step + 1) / onboardingMock.steps.length) * 100
    const choices = step.options
      ? `<div class="choice-grid">${step.options.map((option, index) => `<button class="${index === state.selected ? 'active' : ''}" data-choice="${index}">${option}</button>`).join('')}</div>`
      : `<p class="choice-label">연령대</p><div class="choice-row">${step.ageOptions.map((option, index) => `<button class="${index === 1 ? 'active' : ''}" data-choice="age-${index}">${option}</button>`).join('')}</div><p class="choice-label">성별</p><div class="choice-row">${step.genderOptions.map(option => `<button data-choice="gender-${option.value}">${option.label}</button>`).join('')}</div>`

    return `<main class="onboarding-page"><div class="auth-logo">${renderLogo()}</div><section class="onboarding-card"><div class="step-head"><b>STEP 0${state.step + 1}/0${onboardingMock.steps.length}</b><span>${step.label}</span></div><div class="progress"><i style="width:${progress}%"></i></div><h1>${step.title}</h1>${choices}<footer class="step-footer"><button class="later" data-view="home">나중에 하기</button><button class="primary" data-action="next-step">${state.step === onboardingMock.steps.length - 1 ? '시작' : '다음'} →</button></footer></section></main>`
  }

  return {
    intro: introView,
    login: () => authView(false),
    signup: () => authView(true),
    onboarding: onboardingView
  }
}
