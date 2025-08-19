#!/bin/bash

echo "=== WSL 네트워크 진단 ==="

# 1. WSL IP 확인
echo "1. WSL IP 주소:"
hostname -I

# 2. Windows 호스트 IP 확인
echo "2. Windows 호스트 IP:"
cat /etc/resolv.conf | grep nameserver

# 3. 기본 네트워크 연결 테스트
echo "3. 로컬 연결 테스트:"
echo "localhost 핑 테스트:"
ping -c 1 localhost

echo "4. 포트 8080 연결 테스트:"
timeout 3 bash -c "</dev/tcp/localhost/8080" && echo "localhost:8080 연결됨" || echo "localhost:8080 연결 실패"

# 5. Windows IP로 테스트
echo "5. Windows IP 테스트:"
WINDOWS_IP=$(cat /etc/resolv.conf | grep nameserver | awk '{print $2}')
echo "Windows IP: $WINDOWS_IP"

if [ -n "$WINDOWS_IP" ]; then
    echo "Windows IP 핑 테스트:"
    ping -c 1 $WINDOWS_IP
    
    echo "Windows IP:8080 포트 테스트:"
    timeout 3 bash -c "</dev/tcp/$WINDOWS_IP/8080" && echo "$WINDOWS_IP:8080 연결됨" || echo "$WINDOWS_IP:8080 연결 실패"
fi

# 6. curl 설치 확인
echo "6. curl 설치 확인:"
which curl

# 7. 간단한 HTTP 테스트
echo "7. HTTP 테스트:"
if [ -n "$WINDOWS_IP" ]; then
    echo "Windows IP로 HTTP 테스트:"
    curl -m 5 http://$WINDOWS_IP:8080/actuator/health 2>&1 || echo "HTTP 연결 실패"
fi

echo "localhost로 HTTP 테스트:"
curl -m 5 http://localhost:8080/actuator/health 2>&1 || echo "HTTP 연결 실패"