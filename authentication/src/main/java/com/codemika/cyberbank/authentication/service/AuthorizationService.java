package com.codemika.cyberbank.authentication.service;

import com.codemika.cyberbank.authentication.dto.RqCreateUser;
import com.codemika.cyberbank.authentication.dto.RsInfoUser;
import com.codemika.cyberbank.authentication.entity.RoleEntity;
import com.codemika.cyberbank.authentication.entity.RoleUserEntity;
import com.codemika.cyberbank.authentication.entity.UserEntity;
import com.codemika.cyberbank.authentication.repository.RoleRepository;
import com.codemika.cyberbank.authentication.repository.RoleUserRepository;
import com.codemika.cyberbank.authentication.repository.UserRepository;
import com.codemika.cyberbank.authentication.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.codemika.cyberbank.authentication.constants.RoleConstants.IS_USER_ROLE_EXIST_CLAIMS_KEY;
import static com.codemika.cyberbank.authentication.constants.RoleConstants.USER_ROLE;

/**
 * Сервис для авторизации
 */

@Data
@Service
public class AuthorizationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleUserRepository roleUserRepository;
    private final JwtUtil jwtUtil;
    private boolean check = false; // переменная проверенного пользователя
    private ResponseEntity<?> errorMessage; // сообщение, если что-то не так при регистрации

    /**
     * Регистрация пользователя
     *
     * @param rq запрос на создание пользователя
     * @return результат и новый токен
     */
    public ResponseEntity<?> registration(RqCreateUser rq) {
        if (!check) {
            return errorMessage;
        }

        UserEntity newUser = new UserEntity()
                .setName(rq.getName())
                .setSurname(rq.getSurname())
                .setPatronymic(rq.getPatronymic())
                .setEmail(rq.getEmail())
                .setPhone(rq.getPhone())
                .setPassword(rq.getPassword());

        Optional<RoleEntity> role = roleRepository.findByRole(USER_ROLE);

        if (!role.isPresent()) {
            return ResponseEntity.badRequest().body("Данная роль не существует");
        }

        userRepository.save(newUser);

        RoleUserEntity roleUser = new RoleUserEntity()
                .setUser(newUser)
                .setRole(role.get());

        roleUserRepository.save(roleUser);

        Claims claims = Jwts.claims();
        claims.put("id", newUser.getId());
        claims.put("name", newUser.getName());
        claims.put("surname", newUser.getSurname());
        claims.put("patronymic", newUser.getPatronymic());
        claims.put("email", newUser.getEmail());
        claims.put("phone", newUser.getPhone());
        claims.put(IS_USER_ROLE_EXIST_CLAIMS_KEY, true);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Успешная регистрация! Ваш токен для подтверждения личности: " + jwtUtil.generateToken(claims));
    }

    /**
     * Вход пользователя по номеру телефона и паролю
     *
     * @param phone номер телефона
     * @param pass  пароль
     * @return Результат входа и, в случае успеха, новый токен
     */
    public ResponseEntity<?> login(String phone, String pass) {
        Optional<UserEntity> tmpUser = userRepository.findByPhone(phone);
        if (!tmpUser.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Пользователь с таким номером телефона не существует!");
        }

        if (!tmpUser.get().getPassword().equals(pass)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Пароль или номер телефона неверны");
        }

        List<RoleUserEntity> userRoles = roleUserRepository.findAllByUser(tmpUser.get());

        Claims claims = Jwts.claims();
        claims.put("id", tmpUser.get().getId());
        claims.put("name", tmpUser.get().getName());
        claims.put("surname", tmpUser.get().getSurname());
        claims.put("patronymic", tmpUser.get().getPatronymic());
        claims.put("email", tmpUser.get().getEmail());
        claims.put("phone", tmpUser.get().getPhone());

        claims.put(IS_USER_ROLE_EXIST_CLAIMS_KEY, false);
        claims.put("is_moder_role", false);
        claims.put("is_tester_role", false);
        claims.put("is_hacker_role", false);

        for (RoleUserEntity userRole : userRoles) {
            if (Objects.equals(userRole.getRole().getRole(), "USER")) {
                claims.replace(IS_USER_ROLE_EXIST_CLAIMS_KEY, true);
            } else if (Objects.equals(userRole.getRole().getRole(), "MODER")) {
                claims.replace("is_moder_role", true);
            } else if (Objects.equals(userRole.getRole().getRole(), "TESTER")) {
                claims.replace("is_tester_role", true);
            } else if (Objects.equals(userRole.getRole().getRole(), "HACKER")) {
                claims.replace("is_hacker_role", true);
            }
        }

        //TODO: Добавить карты, кредиты и т.д.
        String result = String.format("Добро пожаловать, %s %s %s!\n" +
                        "Ваша эл. почта: %s\n" +
                        "Ваш номер телефона: %s\n" +
                        //"Ваши карты: \n" +
                        "Ваш новый токен: ",
                tmpUser.get().getSurname(), tmpUser.get().getName(),
                tmpUser.get().getPatronymic(), tmpUser.get().getEmail(),
                phone) + jwtUtil.generateToken(claims);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(result);
    }

    /**
     * Вход пользователя по токену (Просто смена токена и выдача инфы)
     *
     * @param token токен
     * @return информация о пользователе
     */
    public ResponseEntity<?> login(String token) {
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Неверный токен!");
        }

        Claims claims = jwtUtil.getClaims(token);

        if (jwtUtil.getClaims(token).get("role", String.class) == null) {
            claims.put("role", "USER");
        }

        String name = claims.get("name", String.class);
        String surname = claims.get("surname", String.class);
        String patronymic = claims.get("patronymic", String.class);
        String email = claims.get("email", String.class);
        String phone = claims.get("phone", String.class);

        //TODO: Добавить карты, кредиты и т.д.
        String result = String.format("Добро пожаловать, %s %s %s!\n" +
                "Ваша эл. почта: %s\n" +
                "Ваш номер телефона: %s\n" +
                //"Ваши карты: \n" +
                "Ваш новый токен: ", surname, name, patronymic, email, phone) + jwtUtil.generateToken(claims);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(result);
    }

    /**
     * Поиск всех пользователей на сайте
     *
     * @return все пользователи нашего банка
     */
    public ResponseEntity<?> getAllUsers() {
        List<UserEntity> users = userRepository.findAll();
        if (users.isEmpty())
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body("У нас ещё нет ни одного пользователя... Хотите стать первым?🥺");

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(users);
    }

    /**
     * Поиск пользователя по id (только для модеров)
     *
     * @param id идентификационный номер пользователя
     * @return искомого пользователя
     */
    public ResponseEntity<?> getUserById(Long id) {
        Optional<UserEntity> user = userRepository.findById(id);
        if (!user.isPresent())
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body("Данный пользователь не существует!");

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(user.get());
    }

    /**
     * Поиск пользователя по эл. почте
     *
     * @param email эл. почта
     * @return имя, фамилию и отчество требуемого пользователя
     */
    public ResponseEntity<?> getUserByEmail(String email) {
        // todo: вынести пользователя в переменную и работать с результатом
        if (!userRepository.findByEmail(email).isPresent())
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body("Данный пользователь не существует!");

        UserEntity rq = userRepository.findByEmail(email).get();
        RsInfoUser rs = new RsInfoUser()
                .setName(rq.getName())
                .setSurname(rq.getSurname())
                .setPatronymic(rq.getPatronymic());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(rs);
    }

    /**
     * Поиск пользователя по номеру телефона
     *
     * @param phone номер телефона
     * @return имя, фамилию и отчество требуемого пользователя
     */
    public ResponseEntity<?> getUserByPhone(String phone) {
        // todo: вынести пользователя в переменную и работать с результатом
        if (!userRepository.findByPhone(phone).isPresent())
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body("Данный пользователь не существует!");

        UserEntity rq = userRepository.findByPhone(phone).get();
        RsInfoUser rs = new RsInfoUser()
                .setName(rq.getName())
                .setSurname(rq.getSurname())
                .setPatronymic(rq.getPatronymic());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(rs);
    }

    //Валидация пользователя по id
    public Boolean validateUserByToken(String token) {
        Claims claims = jwtUtil.getClaims(token);
        Long id = claims.get("id", Long.class);
        return userRepository.existsById(id);
    }

    public ResponseEntity<?> becomeModer(Long idNewModer) {
        // todo: проверять, что у пользователя уже нет роли MODER

        Optional<UserEntity> user = userRepository.findById(idNewModer);
        if (!user.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Данный пользователь не существует!");
        }

        if (!roleRepository.findByRole("MODER").isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Извините, произошла ошибка! Данной роли не существует.");
        }

        RoleUserEntity newRoleUser = new RoleUserEntity()
                .setUser(user.get())
                .setRole(roleRepository.findByRole("MODER").get());
        roleUserRepository.save(newRoleUser);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(String.format("Пользователь %s успешно получил роль MODER!", idNewModer));
    }
}
