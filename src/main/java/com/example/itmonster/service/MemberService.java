package com.example.itmonster.service;

import com.example.itmonster.controller.request.MemberStacksDto;
import com.example.itmonster.controller.request.SignupRequestDto;
import com.example.itmonster.controller.request.SmsRequestDto;
import com.example.itmonster.controller.response.BookmarkDto;
import com.example.itmonster.controller.response.CompletedQuestDto;
import com.example.itmonster.controller.response.MemberResponseDto;
import com.example.itmonster.controller.response.MyPageResponseDto;
import com.example.itmonster.controller.response.ResponseDto;
import com.example.itmonster.controller.response.SocialLoginResponseDto;
import com.example.itmonster.controller.response.StackDto;
import com.example.itmonster.domain.Bookmark;
import com.example.itmonster.domain.Folio;
import com.example.itmonster.domain.Follow;
import com.example.itmonster.domain.Member;
import com.example.itmonster.domain.RoleEnum;
import com.example.itmonster.domain.Squad;
import com.example.itmonster.domain.StackOfMember;
import com.example.itmonster.exceptionHandler.CustomException;
import com.example.itmonster.exceptionHandler.ErrorCode;
import com.example.itmonster.repository.BookmarkRepository;
import com.example.itmonster.repository.FolioRepository;
import com.example.itmonster.repository.FollowRepository;
import com.example.itmonster.repository.MemberRepository;
import com.example.itmonster.repository.SquadRepository;
import com.example.itmonster.repository.StackOfMemberRepository;
import com.example.itmonster.security.UserDetailsImpl;
import com.example.itmonster.security.jwt.JwtDecoder;
import com.example.itmonster.utils.RedisUtil;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final StackOfMemberRepository stackOfMemberRepository;
	private final FollowRepository followRepository;
	private final PasswordEncoder passwordEncoder;
	private final AwsS3Service s3Service;
	private final FolioRepository folioRepository;
	private final RedisUtil redisUtil;
	private final SmsService smsService;
	private final JwtDecoder jwtDecoder;
	private final SquadRepository squadRepository;
	private final BookmarkRepository bookmarkRepository;


	String emailPattern = "^[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*@[0-9a-zA-Z]([-_.]?[0-9a-zA-Z])*.[a-zA-Z]{2,3}$"; //????????? ????????? ??????
	String nicknamePattern = "^[a-zA-Z0-9???-??????-??????-???~!@#$%^&*]{2,8}$"; // ?????????????????? , ?????? , ?????????????????? 2~8?????????
	String passwordPattern = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z~!@#$%^&*\\d]{8,20}$"; //  ??????????????????,?????? ?????? 8????????? 20???;
	String phoneNumPattern = "^(\\d{11})$"; // 11?????? ??????

//    @Value("${spring.admin.token}") // ????????? ?????????
//    String ADMIN_TOKEN;

	@Transactional
	public String signupUser(SignupRequestDto requestDto) throws IOException {

		String profileUrl = s3Service.getSavedS3ImageUrl(requestDto.getProfileImage());
		if (!Objects.equals(redisUtil.getData(requestDto.getPhoneNumber()), "true")) {
			throw new CustomException(ErrorCode.FAILED_VERIFYING_PHONENUMBER);
		}

		checkEmailPattern(requestDto.getEmail());//username ????????? ?????? ?????? ?????? ??????????????? ??????
		checkNicknamePattern(requestDto.getNickname());//nickname ????????? ?????? ?????? ?????? ??????????????? ??????
		checkPasswordPattern(requestDto.getPassword());//password ????????? ?????? ?????? ?????? ??????????????? ??????
		checkPhoneNumber(requestDto.getPhoneNumber());

		String password = passwordEncoder.encode(requestDto.getPassword()); // ???????????? ?????????

		Member member = Member.builder()
			.email(requestDto.getEmail())
			.nickname(requestDto.getNickname())
			.password(password)
			.profileImg(profileUrl)
			.phoneNumber(requestDto.getPhoneNumber())
			.role(RoleEnum.USER)
			.className("")
			.build();
		memberRepository.save(member);

		// ??? ??????????????? ??????
		folioRepository.save(Folio.builder()
			.title(member.getNickname() + "?????? ????????????????????????.")
			.member(member)
			.build());
		//????????? ???????????? REDIS ??????
		redisUtil.deleteData(requestDto.getPhoneNumber());

		return "??????????????? ???????????????";
	}


	@Transactional
	public MemberResponseDto updateMemberInfo(Member member, SignupRequestDto requestDto)
		throws Exception {

		Member updateUser = memberRepository.findById(member.getId())
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); //???????????? ?????? ??????
		if(Objects.equals(requestDto.getNickname(), updateUser.getNickname())){}
		else if (requestDto.getNickname() != null) { //???????????? ?????? ???????????????
			checkNicknamePattern(requestDto.getNickname()); //????????? ????????? ??????
			if (memberRepository.existsByNickname(requestDto.getNickname())) { //????????? ????????????
				throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
			}
			updateUser.updateNickname(requestDto.getNickname());
		}


		if(requestDto.getProfileImage() != null){
			String profileImg = s3Service.getSavedS3ImageUrl(requestDto.getProfileImage());
			updateUser.updateProfileImg(profileImg);
		}

		updateUser.updateClassName(requestDto.getClassName());
		memberRepository.save(updateUser);

		return memberResponseBuilder(updateUser);
	}


	@Transactional
	public ResponseDto<Boolean> followMember(Long memberId, Member me) {
		Member member = memberRepository.findById(memberId).orElseThrow(
			() -> new CustomException(ErrorCode.USER_NOT_FOUND)); // ????????? ??? ?????? ??????

		if (followRepository.findByFollowingIdAndMeId(  // ????????? ??? ??? ????????? ???????????????
			memberId, me.getId()) == null) {
			followRepository.save(Follow.builder()
				.me(me)
				.following(member)
				.build());
			memberRepository.save(member);
			return ResponseDto.success(Boolean.TRUE);

		} else { //????????? ?????? ????????? ??????

			Follow follow = followRepository.findByFollowingIdAndMeId(
				memberId, me.getId());
			followRepository.delete(follow);
			memberRepository.save(member);

			return ResponseDto.success(Boolean.FALSE);
		}
	}

	@Transactional
	public ResponseDto<List<String>> addStack(MemberStacksDto memberStacksDto,
		Member member) { // ???????????? ??????
		stackOfMemberRepository.deleteAllByMemberId(member.getId());
		List<String> stacks = memberStacksDto.getStacks();
		for (String stackname : stacks) {
			StackOfMember stack = StackOfMember.builder()
				.stackName(stackname)
				.member(member).build();

			stackOfMemberRepository.save(stack);
		}

		return ResponseDto.success(stacks);
	}

	public List<StackDto> getStackList(Member member) {
		List<StackDto> stacks = new ArrayList<>();
		List<StackOfMember> stackOfMemberList = stackOfMemberRepository.findByMemberId(
			member.getId());
		if (stackOfMemberList.size() == 0L) {
			return stacks;
		}

		for (StackOfMember stack : stackOfMemberList) {
			stacks.add(new StackDto(stack.getStackName()));
		}

		return stacks;
	}

	@Cacheable(value = "monsterOfMonthCaching")
	@Transactional(readOnly = true)
	public List<MemberResponseDto> showTop3Following() {
		List<Member> members = memberRepository.findTop3ByOrderByFollowCounter();
		List<MemberResponseDto> responseDtoList = new ArrayList<>();
		for (Member member : members) {
			responseDtoList.add(memberResponseBuilder(member));

		}
		return responseDtoList;
	}


	//username ????????????
	public ResponseDto<String> checkUsername(SignupRequestDto requestDto) {
		checkEmailPattern(requestDto.getEmail());
		if (memberRepository.existsByEmail(requestDto.getEmail())) {
			throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
		}
		return ResponseDto.success("??????????????? ??????????????????.");
	}

	public ResponseDto<String> checkNickname(SignupRequestDto requestDto) {
		checkNicknamePattern(requestDto.getNickname());
		if (memberRepository.existsByNickname(requestDto.getNickname())) {
			throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
		}
		return ResponseDto.success("?????? ????????? ??????????????????.");
	}


	public MemberResponseDto memberInfo(Member member) {

		return memberResponseBuilder(member);
	}

	public MyPageResponseDto getMyPage(Long memberId, String token) {

		Member member = memberRepository.findById(memberId).orElseThrow(
			() -> new CustomException(ErrorCode.USER_NOT_FOUND));
		Folio folio = folioRepository.findByMemberId(memberId);

		List<Squad> squadList = squadRepository.findAllByMember(member);
		List<CompletedQuestDto> completedQuestDtoList = new ArrayList<>();

		for (Squad squad : squadList) {
			if (squad.getQuest().getIsComplete()) {
				completedQuestDtoList.add(CompletedQuestDto.builder()
					.questId(squad.getId())
					.questTitle(squad.getQuest().getTitle())
					.build());
			}
		}

		if (token != null) {
			String username = jwtDecoder.decodeUsername(token.substring(7));
			Member me = memberRepository.findByEmail(username).orElseThrow(
				() -> new CustomException(ErrorCode.INVALID_AUTH_TOKEN));

			return myPageResponseBuilder(member, folio, completedQuestDtoList, true,
				followRepository.existsByFollowingAndMe(member, me));
		}  // ????????? ????????????

		return myPageResponseBuilder(member, folio, completedQuestDtoList, false, false);

	}

	@Transactional(readOnly = true)
	public List<BookmarkDto> getMyBookmark(String token){
		String username = jwtDecoder.decodeUsername(token.substring(7));
		Member member = memberRepository.findByEmail(username)
			.orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
		List<Bookmark> bookmarks = bookmarkRepository.findAllByMarkedMember(member);
		return bookmarks.stream().map(Bookmark::getQuest).map(BookmarkDto::new).collect(Collectors.toList());
	}

	//??????????????? ????????? ?????? ??????
	public ResponseDto<SocialLoginResponseDto> socialUserInfo(UserDetailsImpl userDetails) {
		//????????? ??? user ?????? ??????
		Member member = memberRepository.findBySocialId(userDetails.getMember().getSocialId())
			.orElseThrow(
				() -> new CustomException(ErrorCode.USER_NOT_FOUND));

		//?????? user???????????? dto??? ???????????? ????????????
		SocialLoginResponseDto socialLoginResponseDto = new SocialLoginResponseDto(member, true);
		return ResponseDto.success(socialLoginResponseDto);
	}


	public void checkEmailPattern(String email) {
		if (email == null) {
			throw new CustomException(ErrorCode.EMPTY_EMAIL);
		}
		if (email.equals("")) {
			throw new CustomException(ErrorCode.EMPTY_EMAIL);
		}
		if (!Pattern.matches(emailPattern, email)) {
			throw new CustomException(ErrorCode.EMAIL_WRONG);
		}
		if (memberRepository.findByEmail(email).isPresent()) {
			throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
		}
	}


	public void checkPasswordPattern(String password) {
		if (password == null) {
			throw new CustomException(ErrorCode.EMPTY_PASSWORD);
		}
		if (password.equals("")) {
			throw new CustomException(ErrorCode.EMPTY_PASSWORD);
		}
		if (8 > password.length() || 20 < password.length()) {
			throw new CustomException(ErrorCode.PASSWORD_LEGNTH);
		}
		if (!Pattern.matches(passwordPattern, password)) {
			throw new CustomException(ErrorCode.PASSWORD_WRONG);
		}
	}


	public void checkNicknamePattern(String nickname) {
		if (nickname == null) {
			throw new CustomException(ErrorCode.EMPTY_NICKNAME);
		}
		if (nickname.equals("")) {
			throw new CustomException(ErrorCode.EMPTY_NICKNAME);
		}
		if (2 > nickname.length() || 8 < nickname.length()) {
			throw new CustomException(ErrorCode.NICKNAME_LEGNTH);
		}
		if (!Pattern.matches(nicknamePattern, nickname)) {
			throw new CustomException(ErrorCode.NICKNAME_WRONG);
		}
	}

	public void checkPhoneNumber(String phoneNum) {
		if (phoneNum == null) {
			throw new CustomException(ErrorCode.EMPTY_PHONENUMBER);
		}
		if (phoneNum.equals("")) {
			throw new CustomException(ErrorCode.EMPTY_PHONENUMBER);
		}
		if (phoneNum.length() != 11) {
			throw new CustomException(ErrorCode.PHONENUMBER_LENGTH);
		}
		if (!Pattern.matches(phoneNumPattern, phoneNum)) {
			throw new CustomException(ErrorCode.PHONENUMBER_WRONG);
		}

	}

	@CacheEvict(value = "monsterOfMonthCaching", allEntries = true)
	public void deleteCacheTest() {
	}

	@CacheEvict(value = "monsterOfMonthCaching", allEntries = true)
	@Scheduled(cron = "0 0 0 * * *")
	public void deleteCache() {
	}


	public ResponseDto<String> sendSmsForSignup(SmsRequestDto requestDto)
		throws NoSuchAlgorithmException, InvalidKeyException {
		if (memberRepository.existsByPhoneNumber(requestDto.getPhoneNumber())) {
			throw new CustomException(ErrorCode.DUPLICATE_PHONENUMBER);
		}
		checkPhoneNumber(requestDto.getPhoneNumber());
		String response = smsService.sendSms(requestDto.getPhoneNumber());
		if (response.contains("errors")) {
			throw new CustomException(ErrorCode.FAILED_MESSAGE);
		}
		return ResponseDto.success(response);
	}

	public Boolean confirmPhoneNumber(SmsRequestDto requestDto) {
		String phoneNumber = requestDto.getPhoneNumber();
		if (Objects.equals(redisUtil.getData(phoneNumber), "true")) {
			return Boolean.TRUE;
		}// ?????? ???????????? ????????? ?????? ??????

		if (!Objects.equals(redisUtil.getData(phoneNumber), requestDto.getAuthNumber())) {
			throw new CustomException(ErrorCode.FAILED_VERIFYING_AUTH);
		}// ??????????????? ???????????? ????????????

		redisUtil.deleteData(requestDto.getPhoneNumber());
		redisUtil.setDataExpire(phoneNumber, "true", 300);
		return Boolean.TRUE;
	}


	public MemberResponseDto memberResponseBuilder(Member member) {
		return MemberResponseDto.builder()
			.id(member.getId())
			.nickname(member.getNickname())
			.profileImage(member.getProfileImg())
			.stacks(getStackList(member))
			.className(member.getClassName())
			.followCnt(member.getFollowCounter())
			.folioTitle(folioRepository.findByMemberId(member.getId()).getTitle())
			.build();
	}

	public MyPageResponseDto myPageResponseBuilder(Member member, Folio folio,
		List<CompletedQuestDto> completedQuestDtos, Boolean login, Boolean followStatus) {

		return MyPageResponseDto.builder()
			.memberId(member.getId())
			.nickname(member.getNickname())
			.className(member.getClassName())
			.profileUrl(member.getProfileImg())
			.stackList(getStackList(member))
			.title(folio.getTitle())
			.notionUrl(folio.getNotionUrl())
			.githubUrl(folio.getGithubUrl())
			.blogUrl(folio.getBlogUrl())
			.completedQuestList(completedQuestDtos)
			.followStatus(followStatus)
			.loginStatus(login)
			.build();
	}
}

//????????? ??? ????????? ?????? ?????? ??? ?????? API ????????? ?????? ?????? ????????? ??????
//    public ResponseEntity adminAuthorization(AdminRequestDto requestDto, UserDetailsImpl userDetails) {
//        // ????????? ROLE ??????
//        UserRoleEnum role = UserRoleEnum.USER;
//        if (requestDto.isAdmin()) {
//            if (!requestDto.getAdminToken().equals(ADMIN_TOKEN)) {
//                throw new CustomException(ErrorCode.INVALID_AUTHORITY_WRONG); // ???????????? ??????
//            }
//            role = UserRoleEnum.ADMIN;
//        }
//
//        //?????? ??????
//        userDetails.getUser().setRole(role);
//        //????????? ?????? ??????
//        userRepository.save(userDetails.getUser());
//        return new ResponseEntity("????????? ???????????? ?????????????????????", HttpStatus.OK);
//    }




