module Page.Login exposing (view, update, Model, Msg, initModel, ExternalMsg(..))

{-| The login page.
-}

import Route exposing (Route)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Views.Form as Form
import Views.StravaLogin as StravaLogin
import Json.Decode as Decode exposing (field, decodeString, string, Decoder)
import Json.Decode.Pipeline as Pipeline exposing (optional, decode)
import Validate exposing (..)
import Data.Session as Session exposing (Session)
import Http
import Request.User exposing (storeSession)
import Util exposing ((=>))
import Data.User as User exposing (User)


-- MODEL --


type alias Model =
    { errors : List Error
    , email : String
    , password : String
    }


initModel : Model
initModel =
    { errors = []
    , email = ""
    , password = ""
    }



-- VIEW --


view : Session -> Model -> Html Msg
view session model =
    StravaLogin.view



-- div [ class "auth-page" ]
--     [ div [ class "container page" ]
--         [ div [ class "row" ]
--             [ div [ class "col-md-6 offset-md-3 col-xs-12" ]
--                 [ h1 [ class "text-xs-center" ] [ text "Sign in" ]
--                 , p [ class "text-xs-center" ]
--                     [ a [ Route.href Route.Register ]
--                         [ text "Need an account?" ]
--                     ]
--                 , Form.viewErrors model.errors
--                 , viewForm
--                 ]
--             ]
--         ]
--     ]


viewForm : Html Msg
viewForm =
    Html.form [ onSubmit SubmitForm ]
        [ Form.input
            [ class "form-control-lg"
            , placeholder "Email"
            , onInput SetEmail
            ]
            []
        , Form.password
            [ class "form-control-lg"
            , placeholder "Password"
            , onInput SetPassword
            ]
            []
        , button [ class "btn btn-lg btn-primary pull-xs-right" ]
            [ text "Sign in" ]
        ]



-- UPDATE --


type Msg
    = SubmitForm
    | SetEmail String
    | SetPassword String
    | LoginCompleted (Result Http.Error User)


type ExternalMsg
    = NoOp
    | SetUser User


update : String -> Msg -> Model -> ( ( Model, Cmd Msg ), ExternalMsg )
update apiUrl msg model =
    case msg of
        SubmitForm ->
            case validate model of
                [] ->
                    { model | errors = [] }
                        => Http.send
                            LoginCompleted
                            (Request.User.login apiUrl model)
                        => NoOp

                errors ->
                    { model | errors = errors }
                        => Cmd.none
                        => NoOp

        SetEmail email ->
            { model | email = email }
                => Cmd.none
                => NoOp

        SetPassword password ->
            { model | password = password }
                => Cmd.none
                => NoOp

        LoginCompleted (Err error) ->
            let
                errorMessages =
                    case error of
                        Http.BadStatus response ->
                            response.body
                                |> decodeString (field "errors" errorsDecoder)
                                |> Result.withDefault []

                        _ ->
                            [ "unable to process registration" ]
            in
                { model | errors = List.map (\errorMessage -> Form => errorMessage) errorMessages }
                    => Cmd.none
                    => NoOp

        LoginCompleted (Ok user) ->
            model
                => Cmd.batch [ storeSession user, Route.modifyUrl Route.Home ]
                => SetUser user



-- VALIDATION --


type Field
    = Form
    | Email
    | Password


{-| Recording validation errors on a per-field basis facilitates displaying
them inline next to the field where the error occurred.
I implemented it this way out of habit, then realized the spec called for
displaying all the errors at the top. I thought about simplifying it, but then
figured it'd be useful to show how I would normally model this data - assuming
the intended UX was to render errors per field.
(The other part of this is having a view function like this:
viewFormErrors : Field -> List Error -> Html msg
...and it filters the list of errors to render only the ones for the given
Field. This way you can call this:
viewFormErrors Email model.errors
...next to the `email` field, and call `viewFormErrors Password model.errors`
next to the `password` field, and so on.
-}
type alias Error =
    ( Field, String )


validate : Model -> List Error
validate =
    Validate.all
        [ .email >> ifBlank (Email => "email can't be blank.")
        , .password >> ifBlank (Password => "password can't be blank.")
        ]


errorsDecoder : Decoder (List String)
errorsDecoder =
    decode (\email username password -> List.concat [ email, username, password ])
        |> optionalError "email"
        |> optionalError "username"
        |> optionalError "password"


optionalError : String -> Decoder (List String -> a) -> Decoder a
optionalError fieldName =
    let
        errorToString errorMessage =
            String.join " " [ fieldName, errorMessage ]
    in
        optional fieldName (Decode.list (Decode.map errorToString string)) []
