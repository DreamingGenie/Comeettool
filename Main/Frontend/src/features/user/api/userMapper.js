const legacySex = {
  male: 'M',
  female: 'F'
}

function toAge(value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number.parseInt(value, 10)
  return Number.isNaN(parsed) ? null : parsed
}

export function normalizeProfile(profile = {}) {
  const nickname = profile.nickname || ''
  const email = profile.email || ''

  return {
    userId: profile.userId ?? profile.id ?? null,
    email,
    nickname,
    phone: profile.phone || '',
    profileImage: profile.profileImage || '',
    sex: profile.sex || legacySex[profile.gender] || '',
    age: profile.age ?? toAge(profile.ageGroup),
    jobFamily: profile.jobFamily || profile.jobGroup || '',
    jobRole: profile.jobRole || profile.job || '',
    userDescription: profile.userDescription || '',
    userColor: profile.userColor || '#5f6fe5',
    avatarText: (nickname || email || '?').slice(0, 1)
  }
}

export function toProfileUpdatePayload(profile = {}) {
  return {
    nickname: profile.nickname?.trim() || '',
    phone: profile.phone?.trim() || null,
    sex: profile.sex || null,
    age: toAge(profile.age),
    jobFamily: profile.jobFamily || null,
    jobRole: profile.jobRole || null,
    userDescription: profile.userDescription?.trim() || null,
    userColor: profile.userColor || '#5f6fe5'
  }
}

export function toOnboardingPayload(answers = {}) {
  return {
    jobFamily: answers.jobFamily || null,
    jobRole: answers.jobRole || null,
    age: toAge(answers.age),
    sex: answers.sex || null
  }
}

export function normalizeUserSummary(user = {}) {
  const nickname = user.nickname || user.name || ''
  const email = user.email || ''

  return {
    userId: user.userId ?? user.id ?? null,
    nickname,
    email,
    profileImage: user.profileImage || '',
    userColor: user.userColor || '#5f6fe5',
    avatarText: (nickname || email || '?').slice(0, 1)
  }
}
