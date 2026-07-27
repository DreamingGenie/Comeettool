const optionTags = (items, selected) =>
  items.map(item => `<option ${item === selected ? 'selected' : ''}>${item}</option>`).join('')

export function createUserTemplates({
  getCurrentUser,
  profileOptions,
  settingsShell,
  renderSideIcon
}) {
  const profileView = () => {
    const user = getCurrentUser()
    return settingsShell(
      `<form class="profile-settings" data-submit="save"><header class="account-heading"><span>ACCOUNT SETTINGS</span><h1>프로필 설정</h1><p>내 정보를 최신 상태로 유지하고 팀원들에게 나를 소개해 보세요.</p></header><div class="profile-settings-body"><aside class="photo-card"><div class="photo-circle">${renderSideIcon('camera')}<button type="button">＋</button></div><b>프로필 사진</b><small>권장 크기 400 × 400px<br>JPG, PNG · 최대 5MB</small><button type="button" class="upload-photo">사진 업로드</button><p>✓ 얼굴이 잘 보이는 사진을 권장해요.</p></aside><section class="profile-fields"><div class="form-section-title"><b>기본 정보</b><small>서비스에서 표시되는 정보를 입력해 주세요.</small></div><label class="field">닉네임<div class="input-with-meta"><span>♙</span><input name="nickname" value="${user.nickname}" maxlength="10"><small>${user.nickname.length}/10</small></div></label><label class="field">전화번호<input name="phone" value="${user.phone}"></label><div class="form-section-title role-title"><b>직무 및 프로필</b><small>맞춤형 팀 경험을 위해 직무 정보를 선택해 주세요.</small></div><div class="profile-grid"><fieldset><legend>성별</legend>${profileOptions.genders.map(gender => `<label class="radio-card ${user.gender === gender.value ? 'active' : ''}"><input type="radio" name="gender" value="${gender.value}" ${user.gender === gender.value ? 'checked' : ''}> ${gender.label}</label>`).join('')}</fieldset><label class="field">연령대<select name="ageGroup">${optionTags(profileOptions.ageGroups, user.ageGroup)}</select></label><label class="field">직군<select name="jobGroup">${optionTags(profileOptions.jobGroups, user.jobGroup)}</select></label><label class="field">세부 직무<select name="job">${optionTags(profileOptions.jobs, user.job)}</select></label></div><button class="primary profile-submit">저장</button></section></div></form>`,
      'profile'
    )
  }

  const passwordView = () =>
    settingsShell(
      '<div class="password-layout password-settings"><form class="password-card" data-submit="save"><h1>비밀번호 변경</h1><label class="field">현재 비밀번호<div class="password-box"><input name="currentPassword" type="password" required><button type="button" data-action="eye">◉</button></div></label><hr><label class="field">새로운 비밀번호<div class="password-box"><input name="newPassword" type="password" required><button type="button" data-action="eye">◉</button></div></label><div class="strength-label"><b>비밀번호 강도</b><span>약함</span></div><div class="strength-bar"><i></i></div><label class="field">새로운 비밀번호 확인<div class="password-box"><input name="newPasswordConfirm" type="password" required><button type="button" data-action="eye">◉</button></div></label><div class="password-actions"><button class="primary">비밀번호 변경 →</button><button type="button">취소</button></div></form><aside><section class="requirements"><h2>보안 요구사항</h2><article><i>✓</i><div><b>최소 12자 이상</b><p>긴 비밀번호일수록 무차별 대입 공격에 더 안전합니다.</p></div></article><article><i>✓</i><div><b>복잡성</b><p>문자(A-z), 숫자(0-9), 특수문자(!@#)를 조합하세요.</p></div></article><article><i>✓</i><div><b>반복 피하기</b><p>이메일 주소 일부나 단순한 연속 문자를 사용하지 마세요.</p></div></article></section></aside></div>',
      'password'
    )

  return { profile: profileView, password: passwordView }
}
