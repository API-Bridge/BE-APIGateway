package org.example.APIGatewaySvc.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 인증 관련 컨트롤러
 * Auth0 OAuth2 로그인 및 사용자 정보 처리
 */
@Controller
public class AuthController {

    /**
     * 메인 페이지 - 로그인 상태에 따라 다른 내용 표시
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    /**
     * 홈 페이지 - Auth0 로그인 링크 제공
     */
    @GetMapping("/home")
    @ResponseBody
    public String home(@AuthenticationPrincipal OidcUser principal) {
        if (principal != null) {
            return String.format("""
                <html>
                <body>
                    <h2>환영합니다, %s님!</h2>
                    <p>이메일: %s</p>
                    <p><a href="/api/me">사용자 정보 보기 (JSON)</a></p>
                    <p><a href="/logout">로그아웃</a></p>
                </body>
                </html>
                """, 
                principal.getAttribute("name"), 
                principal.getAttribute("email"));
        } else {
            return """
                <html>
                <body>
                    <h2>API Gateway 서비스</h2>
                    <p><a href="/oauth2/authorization/auth0"><strong>🔐 Auth0로 로그인</strong></a></p>
                    <hr>
                    <p><a href="/api/public">공개 API 테스트</a></p>
                    <p><a href="/api/me">인증 필요 API 테스트</a> (로그인 후 사용 가능)</p>
                    <hr>
                    <h3>개발 도구:</h3>
                    <p><a href="/h2-console" target="_blank">H2 데이터베이스 콘솔</a></p>
                    <p><a href="/swagger-ui.html" target="_blank">Swagger API 문서</a></p>
                    <p><a href="/actuator/health" target="_blank">애플리케이션 상태 확인</a></p>
                </body>
                </html>
                """;
        }
    }

    /**
     * 로그아웃 처리
     */
    @GetMapping("/logout")
    @ResponseBody
    public String logout() {
        return """
            <html>
            <body>
                <h2>로그아웃 되었습니다</h2>
                <p><a href="/home">홈으로 돌아가기</a></p>
            </body>
            </html>
            """;
    }
}