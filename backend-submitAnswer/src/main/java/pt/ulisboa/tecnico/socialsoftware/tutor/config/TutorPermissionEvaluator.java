package pt.ulisboa.tecnico.socialsoftware.tutor.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.domain.QuestionAnswer;
import pt.ulisboa.tecnico.socialsoftware.tutor.answer.repository.QuestionAnswerRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.auth.domain.AuthUser;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.dto.CourseDto;
import pt.ulisboa.tecnico.socialsoftware.tutor.course.CourseService;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.domain.Discussion;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.domain.Reply;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.repository.DiscussionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.discussion.repository.ReplyRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Question;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.domain.Topic;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.repository.AssessmentRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.repository.QuestionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.question.repository.TopicRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.questionsubmission.domain.QuestionSubmission;
import pt.ulisboa.tecnico.socialsoftware.tutor.questionsubmission.repository.QuestionSubmissionRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.quiz.repository.QuizRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.domain.User;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.repository.UserRepository;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.UserService;

import java.io.Serializable;

@Component
public class TutorPermissionEvaluator implements PermissionEvaluator {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionSubmissionRepository questionSubmissionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private ReplyRepository replyRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        User user = ((User) authentication.getPrincipal());
        int userId = user.getId();

        if (targetDomainObject instanceof CourseDto) {
            CourseDto courseDto = (CourseDto) targetDomainObject;
            String permissionValue = (String) permission;
            switch (permissionValue) {
                case "EXECUTION.CREATE":
                    return userService.getEnrolledCoursesAcronyms(userId).contains(courseDto.getAcronym() + courseDto.getAcademicTerm());
                case "DEMO.ACCESS":
                    return courseDto.getName().equals("Demo Course");
                default:
                    return false;
            }
        }

        if (targetDomainObject instanceof Integer) {
            int id = (int) targetDomainObject;
            String permissionValue = (String) permission;
            switch (permissionValue) {
                case "DEMO.ACCESS":
                    CourseDto courseDto = courseService.getCourseExecutionById(id);
                    return courseDto.getName().equals("Demo Course");
                case "COURSE.ACCESS":
                    return userService.userHasAnExecutionOfCourse(userId, id);
                case "EXECUTION.ACCESS":
                    return userHasThisExecution(userId, id);
                case "QUESTION.ACCESS":
                    Question question = questionRepository.findQuestionWithCourseById(id).orElse(null);
                    if (question != null) {
                        return userService.userHasAnExecutionOfCourse(userId, question.getCourse().getId());
                    }
                    return false;
                case "TOPIC.ACCESS":
                    Topic topic = topicRepository.findTopicWithCourseById(id).orElse(null);
                    if (topic != null) {
                        return userService.userHasAnExecutionOfCourse(userId, topic.getCourse().getId());
                    }
                    return false;
                case "ASSESSMENT.ACCESS":
                    Integer courseExecutionId = assessmentRepository.findCourseExecutionIdById(id).orElse(null);
                    if (courseExecutionId != null) {
                        return userHasThisExecution(userId, courseExecutionId);
                    }
                    return false;
                case "QUIZ.ACCESS":
                    courseExecutionId = quizRepository.findCourseExecutionIdById(id).orElse(null);
                    if (courseExecutionId != null) {
                        return userHasThisExecution(userId, courseExecutionId);
                    }
                    return false;
                case "TOURNAMENT.PARTICIPANT":
                        return userParticipatesInTournament(userId, id);
                case "SUBMISSION.ACCESS":
                    QuestionSubmission questionSubmission = questionSubmissionRepository.findById(id).orElse(null);
                    if (questionSubmission != null) {
                        boolean hasCourseExecutionAccess = userHasThisExecution(userId, questionSubmission.getCourseExecution().getId());
                        if (user.getRole() == User.Role.STUDENT) {
                            return hasCourseExecutionAccess && questionSubmission.getSubmitter().getId() == userId;
                        } else {
                            return hasCourseExecutionAccess;
                        }
                    }
                    return false;
                case "QUESTION_ANSWER.ACCESS":
                    QuestionAnswer questionAnswer = questionAnswerRepository.findById(id).orElse(null);
                    return questionAnswer != null && questionAnswer.getQuizAnswer().getUser().getId().equals(userId);
                case "DISCUSSION.OWNER":
                    Discussion discussion = discussionRepository.findById(id).orElse(null);
                    return discussion != null && discussion.getUser().getId().equals(userId);
                case "DISCUSSION.ACCESS":
                    discussion = discussionRepository.findById(id).orElse(null);
                    return discussion != null && user.isTeacher() && userHasThisExecution(userId, discussion.getCourseExecution().getId());
                case "REPLY.ACCESS":
                    Reply reply = replyRepository.findById(id).orElse(null);
                    return reply != null && userHasThisExecution(userId, reply.getDiscussion().getCourseExecution().getId());
                default: return false;
            }
        }

        return false;
    }

    private boolean userHasThisExecution(int userId, int courseExecutionId) {
        return userRepository.countUserCourseExecutionsPairById(userId, courseExecutionId) == 1;
    }

    private boolean userParticipatesInTournament(int userId, int tournamentId) {
        return userRepository.countUserTournamentPairById(userId, tournamentId) == 1;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable serializable, String s, Object o) {
        return false;
    }
}
