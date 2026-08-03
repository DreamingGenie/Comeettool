# 프론트엔드 초대 알림 연동 계획

## 목표

- 네비바 프로필 버튼 옆에 알림 SVG 버튼을 추가한다.
- `GET /api/v1/invitations/me` 응답을 알림 목록으로 표시한다.
- 알림 패널에서 초대를 수락하면 `POST /api/v1/invitations/{invitationId}/accept`를 호출한다.
- 수락 후 알림 목록과 팀 스페이스 목록을 즉시 갱신한다.

## 구현 범위

1. `notification` 도메인에 API, Mock API, Store를 분리한다.
2. 화면은 Store를 통해서만 초대 데이터를 읽고 변경한다.
3. `AppTopbar`는 notification 도메인을 직접 import하지 않고 앱 계층에서 주입한 알림 컨텍스트를 사용한다.
4. 알림 SVG, 미확인 개수 배지, 로딩·오류·빈 상태, 초대 수락 버튼을 제공한다.
5. 현재 서버에 초대 거절 API가 없으므로 거절 기능은 포함하지 않는다.

## API

- `GET /api/v1/invitations/me`
- `POST /api/v1/invitations/{invitationId}/accept`

## 검증

- 원격 OpenAPI 명세에서 초대 조회·수락 API 존재 여부 확인
- 프론트엔드 프로덕션 빌드
- 알림 열기, 빈 상태, 오류 후 재시도, 초대 수락 후 목록 제거 동작 확인

