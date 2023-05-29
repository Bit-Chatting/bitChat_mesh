package com.example.bitcaht.Signaling.Handler;

import com.example.bitcaht.Signaling.model.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
@Log4j2
public class SocketHandler extends TextWebSocketHandler {
    //채팅방에 들어간 유저 Map key = roomId, value = 유저 세션
    private Map<String, List<WebSocketSession>> users = new HashMap<>();
    //채팅방과 offer 저장
    private Map<String, String> offerMap = new HashMap<>();

    Integer maxUser = 3;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> messageMap = new ObjectMapper().readValue(message.getPayload(), Map.class);

        /**
         let join_room = {
         type: "join_room",
         room: "roomName"
         }
         socketRef.current.send(JSON.stringify(join_room));
         */
        SignalType signalType = SignalType.valueOf((String) messageMap.get("type"));

        String roomId = (String) messageMap.get("roomName");
        switch (signalType){
            case join_room:

                //채팅방에 있는 유저 정보
                List<WebSocketSession> userSessions = null;
                if(!users.containsKey(roomId)){
                    userSessions = new CopyOnWriteArrayList<>();
                    users.put(roomId, userSessions);
                }else {
                    userSessions = users.get(roomId);
                }


                if(userSessions.size() >= maxUser){
                    session.sendMessage(new TextMessage("room_full"));
                    break;
                }

                //채팅방에 현재 세션 추가
                //추후 메세지 타입 통합하여 메소드 통합 예정
                userSessions.add(session);
                List<String> otherUsers = userSessions.stream()
                        .filter(user -> !user.getId().equals(session.getId()))
                        .map(WebSocketSession::getId)
                        .collect(Collectors.toList());
                session.sendMessage(new TextMessage(
                        new ObjectMapper().writeValueAsString(
                                //type: all_users, data:, otherUsers
                                Map.of("type", "all_users", "data", otherUsers)
                        )
                ));
                break;

            /**
            let offer = {
                type: "offer",
                sdp: sdp,
                room: "roomName"
            }
            socketRef.current.emit(JSON.stringify(join_room));x`
            */
            case offer:
                offerMap.put(roomId, session.getId());
                sendMessageToRoom(session, roomId,"getOffer", messageMap.get("sdp"));
                break;

            case answer:
                sendMessageToRoom(session, roomId,"getAnswer", messageMap.get("sdp"));
                break;

            case candidate:
                sendMessageToRoom(session, roomId,"getCandidate", messageMap.get("candidate"));
                break;
            default:
                log.info("시그널 타입에러");

        }
    }

    //클라이언트에서 접속 성공 시 발생하는 이벤트
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    }

    private void sendMessageToRoom(WebSocketSession session, String roomId, String messageType, Object data) {

        List<WebSocketSession> userSessions = users.get(roomId);
        try {
            for (WebSocketSession user : userSessions) {

                if (!user.getId().equals(session.getId())) {
                    //채팅방에 있는 유저들에게 offer 전송
                    user.sendMessage(new TextMessage(
                            new ObjectMapper().writeValueAsString(
                                    // 보내는 메세지의 type에 대하여 기존에 사용하는 messageType과 동일하게 변경할 수 있는지 확인
                                    Map.of("type", messageType, "data", data)
                            )
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Fail SendMessage : {}", e.getMessage(), e);
        }
    }
}
