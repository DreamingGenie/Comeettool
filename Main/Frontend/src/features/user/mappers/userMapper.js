const unwrapData = response => response?.data ?? response ?? {}

const ageGroupToAge = ageGroup => {
  if (typeof ageGroup === 'number') return ageGroup
  const matchedAge = Number.parseInt(ageGroup, 10)
  return Number.isNaN(matchedAge) ? undefined : matchedAge
}

const ageToAgeGroup = age => {
  const numericAge = Number(age)
  if (!Number.isFinite(numericAge) || numericAge < 10) return ''
  if (numericAge >= 60) return '60대+'
  return `${Math.floor(numericAge / 10) * 10}대`
}

const sexToGender = sex => {
  if (sex === 'M') return 'male'
  if (sex === 'F') return 'female'
  return sex || ''
}

const genderToSex = gender => {
  if (gender === 'male') return 'M'
  if (gender === 'female') return 'F'
  return gender || undefined
}

const removeUndefined = data =>
  Object.fromEntries(
    Object.entries(data).filter(([, value]) => value !== undefined)
  )

export function mapProfileResponse(response) {
  const data = unwrapData(response)
  return {
    id: data.userId,
    email: data.email || '',
    nickname: data.nickname || '',
    phone: data.phone || '',
    profileImage: data.profileImage || '',
    gender: sexToGender(data.sex),
    ageGroup: ageToAgeGroup(data.age),
    jobGroup: data.jobFamily || '',
    job: data.jobRole || '',
    bio: data.userDescription || '',
    color: data.userColor || '#496FBD',
    avatarText: (data.nickname || data.email || '나').slice(0, 1)
  }
}

export function mapProfileRequest(profile) {
  return removeUndefined({
    nickname: profile.nickname,
    phone: profile.phone,
    sex: genderToSex(profile.gender),
    age: ageGroupToAge(profile.ageGroup),
    jobFamily: profile.jobGroup,
    jobRole: profile.job,
    userDescription: profile.bio,
    userColor: profile.color
  })
}

export function mapOnboardingRequest({ answers = {} }) {
  return {
    jobFamily: answers[0] || null,
    jobRole: answers[1] || null,
    age: ageGroupToAge(answers.age) ?? null,
    sex: genderToSex(answers.gender) ?? null
  }
}
