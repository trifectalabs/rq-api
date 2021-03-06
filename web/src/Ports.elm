port module Ports exposing (storeSession, onSessionChange, up, down, setLayer, refreshLayer, zoomLevel, mapBounds, isRouting, hideSources, addSource, setAnchor, movedAnchor, removedAnchor, loadSegments, selectSegment)

import Json.Encode exposing (Value)
import Data.Map exposing (Point)


port storeSession : Maybe String -> Cmd msg


port onSessionChange : (Value -> msg) -> Sub msg


port up : Maybe ( Maybe String, Maybe String, Maybe String ) -> Cmd msg


port down : () -> Cmd msg


port setLayer : String -> Cmd msg


port refreshLayer : String -> Cmd msg


port zoomLevel : (Float -> msg) -> Sub msg


port mapBounds : (( ( Point, Point ), Bool, Bool ) -> msg) -> Sub msg


port isRouting : String -> Cmd msg


port hideSources : List String -> Cmd msg


port addSource :
    ( String, Maybe String, List ( Float, Float ), Maybe Value, Maybe ( Value, Value, Value ) )
    -> Cmd msg


port setAnchor : (( Float, Float ) -> msg) -> Sub msg


port movedAnchor : (( String, Float, Float ) -> msg) -> Sub msg


port removedAnchor : (String -> msg) -> Sub msg


port loadSegments : (() -> msg) -> Sub msg


port selectSegment : (Maybe String -> msg) -> Sub msg
