package com.codemika.cyberbank.authentication.annotation;

import com.codemika.cyberbank.authentication.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;


@Aspect
@Component
@RequiredArgsConstructor
public class CheckRoleAspect {
    private final JwtUtil jwtUtil;

    /**
     * Список всех ролей
     **/

    // Роль для всех пользователей сайта
    private final String USER = "USER";

    // Роль для модераторов банка, имеет повышенный, но не полный доступ к функциям банка.
    private final String MODER = "MODER";

    // Роль для тестировщиков, имеет доступ ко всем функциям банка
    private final String TESTER = "TESTER";

    // Шуточная роль, имеет доступ ко всем функциям банка
    private final String HACKER = "HACKER";

    /**
     * Проверка ролей
     * @param proceedingJoinPoint
     * @param checkRole название роли
     * @return
     * @throws Throwable
     */
    @Around(value = "@annotation(checkRole)")
    public Object checkRole(ProceedingJoinPoint proceedingJoinPoint, CheckRole checkRole) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature(); // получаем сигнатуру метода

        Object[] args = proceedingJoinPoint.getArgs(); // массив значение всех атрибутов метода
        String[] paramsName = methodSignature.getParameterNames(); // массив названий всех атрибутов метода

        int index = Arrays.asList(paramsName).indexOf(checkRole.tokenParamName()); // ищем атрибут, у которого имя из нашей аннотации (по умолчанию token)

        String token = args[index].toString();
        Claims claims = jwtUtil.getClaims(token);

        String role = claims.get("role", String.class);
        if (role == null || role.isEmpty()){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Ваш последний сеанс истёк. Пожалуйста, войдите в свой аккаунт заново!");
        }
        if (getRoleAccessLevel(role) >= getRoleAccessLevel(checkRole.role())) {
            return proceedingJoinPoint.proceed();
        } else {
            //403 - Forbidden - Доступ запрещён
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("Вы не имеете доступа к данной функции.");
        }
    }

    /**
     * Определение уровня доступа роли
     * @param role роль
     * @return уровень доступа(целый от 0 до 3)
     */
    public int getRoleAccessLevel(String role){
        if (role.equals(USER)) return 0;
        if (role.equals(MODER)) return 1;
        if (role.equals(TESTER)) return 2;
        if (role.equals(HACKER)) return 3;
        return 0;
    }
}
